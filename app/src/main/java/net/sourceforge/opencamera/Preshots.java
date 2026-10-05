package net.sourceforge.opencamera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.ApplicationInterface;
import net.sourceforge.opencamera.preview.Preview;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/** Handles the saving of preview shots.
 */
public class Preshots {
    private static final String TAG = "Preshots";

    /** Finds the supported video resolution that's the closest match to the supplied video_width
     *  and video_height.
     */
    public static CameraController.Size adjustResolutionForVideoCapabilities(int video_width, int video_height, ImageSaver.IntRange supported_widths, ImageSaver.IntRange supported_heights, int width_alignment, int height_alignment) {
        if( !supported_widths.contains(video_width) ) {
            double aspect = ((double)video_height) / ((double)video_width);
            video_width = supported_widths.clamp(video_width);
            video_height = (int)(aspect * video_width + 0.5);
            if( MyDebug.LOG )
                Log.d(TAG, "limit video (width) to: " + video_width + " x " + video_height);
        }
        if( !supported_heights.contains(video_height) ) {
            double aspect = ((double)video_height) / ((double)video_width);
            video_height = supported_heights.clamp(video_height);
            video_width = (int)(video_height / aspect + 0.5);
            if( MyDebug.LOG )
                Log.d(TAG, "limit video (height) to: " + video_width + " x " + video_height);
            // test width again
            if( !supported_widths.contains(video_width) ) {
                video_width = supported_widths.clamp(video_width);
                if( MyDebug.LOG )
                    Log.d(TAG, "can't find valid size that preserves aspect ratios! limit video (width) to: " + video_width + " x " + video_height);
            }
        }
        // Adjust for alignment - we could be cleverer and try to find an adjustment that preserves the aspect
        // ratio. But we'd hope that camera preview sizes already satisfy alignments - or if we had to adjust due to
        // being outside the supported widths or heights, then we should have clamped to something that already
        // satisfies the alignments
        int alignment = width_alignment;
        if( video_width % alignment != 0 ) {
            video_width += alignment - (video_width % alignment);
            if( MyDebug.LOG )
                Log.d(TAG, "adjust video width for alignment to: " + video_width);
        }
        alignment = height_alignment;
        if( video_height % alignment != 0 ) {
            video_height += alignment - (video_height % alignment);
            if( MyDebug.LOG )
                Log.d(TAG, "adjust height for alignment to: " + video_height);
        }
        return new CameraController.Size(video_width, video_height);
    }

    private static class MuxerInfo {
        MediaMuxer muxer;
        boolean muxer_started = false;
        int videoTrackIndex = -1;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void encodeVideoFrame(final MediaCodec encoder, MuxerInfo muxer_info, long presentationTimeUs, boolean end_of_stream) throws IOException {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        if( end_of_stream ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    signal end of stream");
            encoder.signalEndOfInputStream();
        }
        while( true ) {
            if( MyDebug.LOG )
                Log.d(TAG, "    start of loop for saving pre-shot");
            final int timeout_us = 10000;
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, timeout_us);
            if( MyDebug.LOG )
                Log.d(TAG, "    outputBufferIndex: " + outputBufferIndex);
            if( outputBufferIndex >= 0 ) {
                bufferInfo.presentationTimeUs = presentationTimeUs;
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                if( outputBuffer == null ) {
                    Log.e(TAG, "getOutputBuffer returned null");
                    throw new IOException();
                }

                if( (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if( bufferInfo.size != 0 ) {
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                    muxer_info.muxer.writeSampleData(muxer_info.videoTrackIndex, outputBuffer, bufferInfo);
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false);

                break;
                /*if( (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 ) {
                    if( MyDebug.LOG ) {
                        if( !end_of_stream ) {
                            Log.e(TAG, "    reached end of stream unexpectedly");
                        }
                        else {
                            Log.d(TAG, "    end of stream reached");
                        }
                    }
                    break;
                }*/
            }
            else {
                if( outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "    INFO_TRY_AGAIN_LATER");
                    /*if( !end_of_stream ) {
                        break;
                    }*/
                }
                else if( outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "    INFO_OUTPUT_FORMAT_CHANGED");
                    muxer_info.videoTrackIndex = muxer_info.muxer.addTrack(encoder.getOutputFormat());
                    muxer_info.muxer.start();
                    muxer_info.muxer_started = true;
                }
            }
        }
    }

    /** Saves the preshot_bitmaps in the request as a video file.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    static void savePreshotBitmaps(final MainActivity main_activity, final ImageSaver image_saver, final ImageSaver.Request request) {
        if( MyDebug.LOG )
            Log.d(TAG, "savePreshotBitmaps");

        main_activity.savingImage(true);

        List<Bitmap> preshot_bitmaps = request.preshot_bitmaps;
        if( MyDebug.LOG )
            Log.d(TAG, "number of preshots: " + preshot_bitmaps.size());

        ApplicationInterface.VideoMethod method = ApplicationInterface.VideoMethod.FILE;
        Uri video_uri = null;
        String video_filename = null;
        ParcelFileDescriptor video_pfd_saf = null;
        MediaMuxer muxer = null;
        //boolean muxer_started = false;
        MuxerInfo muxer_info = new MuxerInfo();
        MediaCodec encoder = null;
        boolean saved_preshots = false;
        try {
            // rotate if necessary
            // see comments in Preview.RefreshPreviewBitmapTask for update_preshot for why we need to rotote
            int rotation_degrees = main_activity.getPreview().getDisplayRotationDegrees(false);
            if( MyDebug.LOG )
                Log.d(TAG, "rotation_degrees: " + rotation_degrees);
            if( rotation_degrees != 0 ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "rotate preshots");
                Matrix matrix = new Matrix();
                matrix.postRotate(-rotation_degrees);
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    Bitmap new_bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                    bitmap.recycle();
                    preshot_bitmaps.set(i, new_bitmap);
                }

            }

            // resize if necessary - need to ensure we have supported dimensions for encoding to video

            int preshot_width = preshot_bitmaps.get(0).getWidth();
            int preshot_height = preshot_bitmaps.get(0).getHeight();
            // in some cases, the preview surface dimensions may not match the original camera preview dimensions
            // note that this alone isn't enough to guarantee being supported for video encoding, but makes sense to start with this value
            CameraController.Size preview_size = main_activity.getPreview().getCurrentPreviewSize();
            int video_width = preview_size.width;
            int video_height = preview_size.height;
            if( (preshot_width > preshot_height) != (video_width > video_height) ) {
                int dummy = video_height;
                //noinspection SuspiciousNameCombination
                video_height = video_width;
                video_width = dummy;
            }
            if( MyDebug.LOG ) {
                Log.d(TAG, "preshot: " + preshot_width + " x " + preshot_height);
                Log.d(TAG, "preview: " + video_width + " x " + video_height);
            }

            long time_s = System.currentTimeMillis();
            final String mime_type = MediaFormat.MIMETYPE_VIDEO_AVC;
            MediaCodecList codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo best_codec_info = null;
            int best_error = 0;
            int best_offset = 0;
            {
                MediaCodecInfo [] codec_infos = codecs.getCodecInfos();
                for(MediaCodecInfo codec_info : codec_infos) {
                    if( !codec_info.isEncoder() ) {
                        continue;
                    }

                    boolean valid = false;
                    String [] types = codec_info.getSupportedTypes();
                    for(String type : types) {
                        if( type.equalsIgnoreCase(mime_type) ) {
                            valid = true;
                            break;
                        }
                    }

                    if( valid ) {
                        MediaCodecInfo.CodecCapabilities capabilities = codec_info.getCapabilitiesForType(mime_type);
                        MediaCodecInfo.VideoCapabilities video_capabilities = capabilities.getVideoCapabilities();
                        if( video_capabilities != null ) {
                            int error_w = Math.abs(video_capabilities.getSupportedWidths().clamp(video_width) - video_width);
                            int error_h = Math.abs(video_capabilities.getSupportedHeights().clamp(video_height) - video_height);
                            int error = error_w*error_h;
                            int offset_w = video_width % video_capabilities.getWidthAlignment();
                            int offset_h = video_height % video_capabilities.getHeightAlignment();
                            int offset = offset_w*offset_h;
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "video_capabilities:");
                                Log.d(TAG, "    width range: " + video_capabilities.getSupportedWidths());
                                Log.d(TAG, "    height range: " + video_capabilities.getSupportedHeights());
                                Log.d(TAG, "    width alignment: " + video_capabilities.getWidthAlignment());
                                Log.d(TAG, "    height alignment: " + video_capabilities.getHeightAlignment());
                                Log.d(TAG, "    error_w: " + error_w);
                                Log.d(TAG, "    error_h: " + error_h);
                                Log.d(TAG, "    offset_w: " + offset_w);
                                Log.d(TAG, "    offset_h: " + offset_h);
                            }
                            // prefer codec that's closest to supporting the width/height; among those, prefer codec with smallest adjustment needed for alignment
                            if( best_codec_info == null || error < best_error || offset < best_offset ) {
                                best_codec_info = codec_info;
                            }
                        }
                    }
                }
            }

            if( best_codec_info == null ) {
                Log.e(TAG, "can't find a valid codecinfo");
                // don't fail - hope for the best that we might find an encoder below anyway
            }
            else {
                MediaCodecInfo.CodecCapabilities capabilities = best_codec_info.getCapabilitiesForType(mime_type);
                MediaCodecInfo.VideoCapabilities video_capabilities = capabilities.getVideoCapabilities();
                Range<Integer> supported_widths = video_capabilities.getSupportedWidths();
                Range<Integer> supported_heights = video_capabilities.getSupportedHeights();
                int width_alignment = video_capabilities.getWidthAlignment();
                int height_alignment = video_capabilities.getHeightAlignment();
                CameraController.Size adjusted_size = adjustResolutionForVideoCapabilities(video_width, video_height, new ImageSaver.IntRange(supported_widths), new ImageSaver.IntRange(supported_heights), width_alignment, height_alignment);
                video_width = adjusted_size.width;
                video_height = adjusted_size.height;
            }
            if( MyDebug.LOG )
                Log.d(TAG, "time for querying codec capabilities: " + (System.currentTimeMillis() - time_s));

            if( MyDebug.LOG )
                Log.d(TAG, "chosen video resolution: " + video_width + " x " + video_height);
            if( preshot_width != video_width || preshot_height != video_height ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "resize preshot bitmaps to: " + video_width + " x " + video_height);
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    Bitmap new_bitmap = Bitmap.createScaledBitmap(bitmap, video_width, video_height, true);
                    bitmap.recycle();
                    preshot_bitmaps.set(i, new_bitmap);
                }
            }

            // apply any post-processing
            ImageSaver.Request preshot_request = request.copy();
            if( preshot_request.is_front_facing ) {
                // we need to mirror for front camera (or not mirror, if the mirror flag was set)
                // for front camera, the preview will typically be mirrored, whilst saved photos are not mirrored, so we need to undo this to be
                // consistent with the main photo
                preshot_request.mirror = !preshot_request.mirror;
            }

            for(int i=0;i<preshot_bitmaps.size();i++) {
                if( MyDebug.LOG )
                    Log.d(TAG, "apply post-processing for preshot bitmap: " + i);
                Bitmap bitmap = preshot_bitmaps.get(i);

                // applying DRO to preshots disabled, as it's slow...
                /*if( request.process_type == Request.ProcessType.HDR && request.jpeg_images.size() == 1 ) {
                    // note, ProcessType.HDR is used for photo modes DRO (if 1 JPEG image) and HDR (if 3 JPEG images) - we only want to apply DRO
                    // to the former
                    List<Bitmap> bitmaps = new ArrayList<>();
                    bitmaps.add(bitmap);
                    if( !processHDR(bitmaps, request, time_s) ) {
                        Log.e(TAG, "failed to apply DRO to preshot bitmap: " + i);
                        throw new IOException();
                    }
                    bitmap = bitmaps.get(0);
                }*/

                PostProcessing.PostProcessBitmapResult postProcessBitmapResult = image_saver.getPostProcessing().postProcessBitmap(preshot_request, null, bitmap, true);
                bitmap = postProcessBitmapResult.bitmap;
                preshot_bitmaps.set(i, bitmap);
            }

            if( MyDebug.LOG )
                Log.d(TAG, "convert preshot bitmaps to video");

            method = main_activity.getApplicationInterface().createOutputVideoMethod();

            if( MyDebug.LOG )
                Log.d(TAG, "method? " + method);
            final String extension = "mp4";
            final int muxer_format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            if( method == ApplicationInterface.VideoMethod.FILE ) {
                File videoFile = main_activity.getApplicationInterface().createOutputVideoFile(true, extension, request.current_date);
                video_filename = videoFile.getAbsolutePath();
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + video_filename);
                muxer = new MediaMuxer(video_filename, muxer_format);
            }
            else {
                Uri uri;
                if( method == ApplicationInterface.VideoMethod.SAF ) {
                    uri = main_activity.getApplicationInterface().createOutputVideoSAF(true, extension, request.current_date);
                }
                else if( method == ApplicationInterface.VideoMethod.MEDIASTORE ) {
                    uri = main_activity.getApplicationInterface().createOutputVideoMediaStore(true, extension, request.current_date);
                }
                else {
                    uri = main_activity.getApplicationInterface().createOutputVideoUri();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "save to: " + uri);
                video_pfd_saf = main_activity.getContentResolver().openFileDescriptor(uri, "rw");
                video_uri = uri;
                muxer = new MediaMuxer(video_pfd_saf.getFileDescriptor(), muxer_format);
            }
            muxer_info.muxer = muxer;

            if( MyDebug.LOG ) {
                Log.d(TAG, "preshot width: " + video_width);
                Log.d(TAG, "preshot height: " + video_height);
            }
            MediaFormat format = MediaFormat.createVideoFormat(mime_type, video_width, video_height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, preshot_bitmaps.size()*500000*8); // 500KB per frame
            format.setString(MediaFormat.KEY_FRAME_RATE, null); // format passed to MediaCodecList.findEncoderForFormat() must not specify a KEY_FRAME_RATE - so we set the KEY_FRAME_RATE later
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            //int videoTrackIndex = muxer.addTrack(format);
            //muxer.start();

            //encoder = MediaCodec.createEncoderByType(mime_type);
            String encoder_name = codecs.findEncoderForFormat(format);
            if( MyDebug.LOG )
                Log.d(TAG, "encoder_name: " + encoder_name);
            if( encoder_name == null ) {
                Log.e(TAG, "failed to find encoder");
                throw new IOException();
            }
            else {
                encoder = MediaCodec.createByCodecName(encoder_name);

                // now set KEY_FRAME_RATE (must be after findEncoderForFormat(), see note above)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 1000/ Preview.preshot_interval_ms);

                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                Surface inputSurface = encoder.createInputSurface();
                encoder.start();

                if( request.store_location ) {
                    muxer.setLocation((float)request.location.getLatitude(), (float)request.location.getLongitude());
                }

                //int videoTrackIndex = -1;
                //int videoTrackIndex = muxer.addTrack(encoder.getOutputFormat());
                //muxer.start();

                long presentationTimeUs = 0;
                for(int i=0;i<preshot_bitmaps.size();i++) {
                    Bitmap bitmap = preshot_bitmaps.get(i);
                    if( MyDebug.LOG )
                        Log.d(TAG, "save pre-shot: " + i + " time: " + presentationTimeUs);
                    //ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());
                    //bitmap.copyPixelsToBuffer(buffer);

                    //int inputBufferIndex = encoder.dequeueInputBuffer(-1);
                    //if( inputBufferIndex >= 0 ) {
                    //    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                    //    inputBuffer.clear();
                    //    inputBuffer.put(buffer);
                    //    encoder.queueInputBuffer(inputBufferIndex, 0, buffer.limit(), presentationTimeUs, 0);
                    //}

                    //encodeVideoFrame(encoder, muxer_info, presentationTimeUs, false);

                    Canvas canvas = inputSurface.lockCanvas(null);
                    int xpos = (canvas.getWidth() - bitmap.getWidth())/2;
                    int ypos = (canvas.getHeight() - bitmap.getHeight())/2;
                    if( MyDebug.LOG )
                        Log.d(TAG, "render at: " + xpos + " , " + ypos);
                    canvas.drawBitmap(bitmap, xpos, ypos, null);
                    inputSurface.unlockCanvasAndPost(canvas);

                    encodeVideoFrame(encoder, muxer_info, presentationTimeUs, false);
                    //if( true )
                    //    throw new IOException(); // test

                    preshot_bitmaps.set(i, null); // so we know this bitmap is recycled
                    bitmap.recycle();
                    presentationTimeUs += (Preview.preshot_interval_ms*1000);
                }

                encodeVideoFrame(encoder, muxer_info, presentationTimeUs, true);
            }

            saved_preshots = true; // success!
        }
        catch(IOException | IllegalStateException e) {
            // ideally want to catch MediaCodec.CodecException, but then entire class would need to target
            // Android L - instead we catch its superclass IllegalStateException
            MyDebug.logStackTrace(TAG, "failed saving preshots video", e);
            // cleanup
            for(int i=0;i<preshot_bitmaps.size();i++) {
                if( MyDebug.LOG )
                    Log.d(TAG, "recycle preshot bitmap: " + i);
                Bitmap bitmap = preshot_bitmaps.get(i);
                if( bitmap != null ) {
                    bitmap.recycle();
                }
            }
        }
        finally {
            if( encoder != null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "stop encoder");
                encoder.stop();
                if( MyDebug.LOG )
                    Log.d(TAG, "release encoder");
                encoder.release();
            }
            if( muxer != null ) {
                if( muxer_info.muxer_started ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "stop muxer");
                    muxer.stop();
                }
                if( MyDebug.LOG )
                    Log.d(TAG, "release muxer");
                muxer.release();
            }
            try {
                if( video_pfd_saf != null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "close video_pfd_saf: " + video_pfd_saf);
                    video_pfd_saf.close();
                }
            }
            catch(IOException e) {
                MyDebug.logStackTrace(TAG, "failed close resources", e);
            }
        }

        if( saved_preshots ) {
            if( MyDebug.LOG )
                Log.d(TAG, "saved preshots successfully");
            main_activity.getApplicationInterface().completeVideo(method, video_uri);
            main_activity.getApplicationInterface().broadcastVideo(method, video_uri, video_filename);
        }

        main_activity.savingImage(false);
    }
}
