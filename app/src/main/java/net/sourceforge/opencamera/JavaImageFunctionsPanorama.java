package net.sourceforge.opencamera;

import android.graphics.Bitmap;

public class JavaImageFunctionsPanorama {
    private static final String TAG = "JavaImageFunctionsPanorama";

    static class ConvertToGreyscaleFunction implements JavaImageProcessing.ApplyFunctionInterface {

        ConvertToGreyscaleFunction() {
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color = pixels[c];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;

                    int value = (int)(0.3* (float) r + 0.59* (float) g + 0.11* (float) b);

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = value << 24;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ComputeDerivativesFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_Ix; // output for x derivatives
        private final Bitmap bitmap_Iy; // output for y derivatives
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ComputeDerivativesFunction(Bitmap bitmap_Ix, Bitmap bitmap_Iy, Bitmap bitmap_in) {
            this.bitmap_Ix = bitmap_Ix;
            this.bitmap_Iy = bitmap_Iy;
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // we could move these to class members for performance, remember we'd have to have a version per-thread
            int [] cache_bitmap_Ix = new int[this_width*this_height];
            int [] cache_bitmap_Iy = new int[this_width*this_height];

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_in[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int Ix = 0, Iy = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        // use Sobel operator

                        //int pixel0 = fast_bitmap_in[thread_index].getPixel(x-1, y-1) >>> 24;
                        //int pixel0 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x-1)] >>> 24;
                        int pixel1 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)] >>> 24;
                        //int pixel2 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x+1)] >>> 24;
                        int pixel3 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x-1)] >>> 24;
                        int pixel5 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x+1)] >>> 24;
                        //int pixel6 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x-1)] >>> 24;
                        int pixel7 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)] >>> 24;
                        //int pixel8 = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x+1)] >>> 24;

                        //int iIx = (pixel2 + 2*pixel5 + pixel8) - (pixel0 + 2*pixel3 + pixel6);
                        //int iIy = (pixel6 + 2*pixel7 + pixel8) - (pixel0 + 2*pixel1 + pixel2);
                        //iIx /= 8;
                        //iIy /= 8;
                        int iIx = (pixel5 - pixel3)/2;
                        int iIy = (pixel7 - pixel1)/2;

                        // convert so we can store in range 0-255

                        iIx = Math.max(iIx, -127);
                        iIx = Math.min(iIx, 128);
                        iIx += 127; // iIx now runs from 0 to 255

                        iIy = Math.max(iIy, -127);
                        iIy = Math.min(iIy, 128);
                        iIy += 127; // iIy now runs from 0 to 255

                        Ix = iIx;
                        Iy = iIy;
                    }

                    //bitmap_Ix.setPixel(x, y, Ix << 24);
                    //bitmap_Iy.setPixel(x, y, Iy << 24);
                    cache_bitmap_Ix[c] = Ix << 24;
                    cache_bitmap_Iy[c] = Iy << 24;
                }
            }

            bitmap_Ix.setPixels(cache_bitmap_Ix, 0, this_width, off_x, off_y, this_width, this_height);
            bitmap_Iy.setPixels(cache_bitmap_Iy, 0, this_width, off_x, off_y, this_width, this_height);
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class CornerDetectorFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_f; // output
        private final Bitmap bitmap_Ix;
        private final Bitmap bitmap_Iy;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_Ix;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_Iy;

        CornerDetectorFunction(float [] pixels_f, Bitmap bitmap_Ix, Bitmap bitmap_Iy) {
            this.pixels_f = pixels_f;
            this.bitmap_Ix = bitmap_Ix;
            this.bitmap_Iy = bitmap_Iy;
            this.width = bitmap_Ix.getWidth();
            this.height = bitmap_Ix.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_Ix = new JavaImageProcessing.FastAccessBitmap[n_threads];
            fast_bitmap_Iy = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_Ix[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_Ix);
                fast_bitmap_Iy[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_Iy);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            final int radius = 2; // radius for corner detector
            final float [] weights = new float[]{1, 4, 6, 4, 1};

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_Ix[thread_index].ensureCache(y-radius, y+radius); // force cache to cover rows needed by this row
                int bitmap_Ix_cache_y = fast_bitmap_Ix[thread_index].getCacheY();
                int y_rel_bitmap_Ix_cache = y-bitmap_Ix_cache_y;
                int [] bitmap_Ix_cache_pixels = fast_bitmap_Ix[thread_index].getCachedPixelsI();

                fast_bitmap_Iy[thread_index].ensureCache(y-radius, y+radius); // force cache to cover rows needed by this row
                int bitmap_Iy_cache_y = fast_bitmap_Iy[thread_index].getCacheY();
                int y_rel_bitmap_Iy_cache = y-bitmap_Iy_cache_y;
                int [] bitmap_Iy_cache_pixels = fast_bitmap_Iy[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    float out = 0;

                    // extra +1 as we won't have derivative info for the outermost pixels (see compute_derivatives)
                    if( x >= radius+1 && x < width-radius-1 && y >= radius+1 && y < height-radius-1 ) {
                        float h00 = 0.0f;
                        float h01 = 0.0f;
                        float h11 = 0.0f;
                        for(int cy=y-radius;cy<=y+radius;cy++) {
                            for(int cx=x-radius;cx<=x+radius;cx++) {
                                int dx = cx - x;
                                int dy = cy - y;

                                int Ix = bitmap_Ix_cache_pixels[(y_rel_bitmap_Ix_cache+dy)*width+(cx)] >>> 24;
                                int Iy = bitmap_Iy_cache_pixels[(y_rel_bitmap_Iy_cache+dy)*width+(cx)] >>> 24;

                                // convert from 0-255 to -127 - +128:
                                Ix -= 127;
                                Iy -= 127;

                                /*float dist2 = dx*dx + dy*dy;
                                const float sigma2 = 0.25f;
                                float weight = exp(-dist2/(2.0f*sigma2)) / (6.28318530718f*sigma2);
                                //float weight = 1.0;
                                weight /= 65025.0f; // scale from (0, 255) to (0, 1)
                                */
                                float weight = weights[2+dx] * weights[2+dy];
                                //weight = 36;

                                h00 += weight*Ix*Ix;
                                h01 += weight*Ix*Iy;
                                h11 += weight*Iy*Iy;
                            }
                        }

                        float det_H = h00*h11 - h01*h01;
                        float tr_H = h00 + h11;
                        //out = det_H - 0.1f*tr_H*tr_H;
                        out = det_H - 0.06f*tr_H*tr_H;
                    }

                    pixels_f[y*width+x] = out;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class LocalMaximumFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_f; // input
        private final byte [] bytes; // output
        private final int width, height;
        private final float corner_threshold;

        LocalMaximumFunction(float [] pixels_f, byte [] bytes, int width, int height, float corner_threshold) {
            this.pixels_f = pixels_f;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
            this.corner_threshold = corner_threshold;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int out = 0;
                    float in = pixels_f[y*width+x];
                    bytes[y*width+x] = (byte)out;

                    if( in >= corner_threshold ) {
                        //out = 255;
                        // best of 3x3:
                        /*if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                            if( in > rsGetElementAt_float(bitmap, x-1, y-1) &&
                                in > rsGetElementAt_float(bitmap, x, y-1) &&
                                in > rsGetElementAt_float(bitmap, x+1, y-1) &&

                                in > rsGetElementAt_float(bitmap, x-1, y) &&
                                in > rsGetElementAt_float(bitmap, x+1, y) &&

                                in > rsGetElementAt_float(bitmap, x-1, y+1) &&
                                in > rsGetElementAt_float(bitmap, x, y+1) &&
                                in > rsGetElementAt_float(bitmap, x+1, y+1)
                                ) {
                                out = 255;
                            }
                        }*/
                        // best of 5x5:
                        if( x >= 2 && x < width-2 && y >= 2 && y < height-2 ) {
                            if( in > pixels_f[(y-2)*width+(x-2)] &&
                                    in > pixels_f[(y-2)*width+(x-1)] &&
                                    in > pixels_f[(y-2)*width+(x)] &&
                                    in > pixels_f[(y-2)*width+(x+1)] &&
                                    in > pixels_f[(y-2)*width+(x+2)] &&

                                    in > pixels_f[(y-1)*width+(x-2)] &&
                                    in > pixels_f[(y-1)*width+(x-1)] &&
                                    in > pixels_f[(y-1)*width+(x)] &&
                                    in > pixels_f[(y-1)*width+(x+1)] &&
                                    in > pixels_f[(y-1)*width+(x+2)] &&

                                    in > pixels_f[(y)*width+(x-2)] &&
                                    in > pixels_f[(y)*width+(x-1)] &&
                                    in > pixels_f[(y)*width+(x+1)] &&
                                    in > pixels_f[(y)*width+(x+2)] &&

                                    in > pixels_f[(y+1)*width+(x-2)] &&
                                    in > pixels_f[(y+1)*width+(x-1)] &&
                                    in > pixels_f[(y+1)*width+(x)] &&
                                    in > pixels_f[(y+1)*width+(x+1)] &&
                                    in > pixels_f[(y+1)*width+(x+2)] &&

                                    in > pixels_f[(y+2)*width+(x-2)] &&
                                    in > pixels_f[(y+2)*width+(x-1)] &&
                                    in > pixels_f[(y+2)*width+(x)] &&
                                    in > pixels_f[(y+2)*width+(x+1)] &&
                                    in > pixels_f[(y+2)*width+(x+2)]
                            ) {
                                out = 255;
                            }
                        }
                    }

                    bytes[y*width+x] = (byte)out;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class PyramidBlendingComputeErrorFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private int [] errors; // error per thread
        private final Bitmap bitmap;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;
        private final int width;

        public PyramidBlendingComputeErrorFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
        }

        @Override
        public void init(int n_threads) {
            errors = new int[n_threads];
            fast_bitmap = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap[i] = new JavaImageProcessing.FastAccessBitmap(bitmap);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                int y_rel_bitmap_cache = y-bitmap_cache_y;
                int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    int r0 = (color0 >> 16) & 0xFF;
                    int g0 = (color0 >> 8) & 0xFF;
                    int b0 = color0 & 0xFF;

                    int color1 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)];
                    int r1 = (color1 >> 16) & 0xFF;
                    int g1 = (color1 >> 8) & 0xFF;
                    int b1 = color1 & 0xFF;

                    int dr = r0 - r1;
                    int dg = g0 - g1;
                    int db = b0 - b1;
                    int diff2 = dr*dr + dg*dg + db*db;
                    if( errors[thread_index] < 2000000000 ) { // avoid risk of overflow
                        errors[thread_index] += diff2;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        public int getError() {
            int total_error = 0;
            for(int error : errors) {
                total_error += error;
            }
            return total_error;
        }
    }

    private static final float [] pyramid_blending_weights = new float[]{0.05f, 0.25f, 0.4f, 0.25f, 0.05f};

    static class ReduceBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                int sy = 2*y;

                fast_bitmap_in[thread_index].ensureCache(sy-2, sy+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int sx = 2*x;

                    if( sx >= 2 && sx < width-2 && sy >= 2 & sy < height-2 ) {

                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        for(int dy=-2;dy<=2;dy++) {
                            for(int dx=-2;dx<=2;dx++) {

                                int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(sx+dx)];
                                //int color = bitmap_in.getPixel(sx+dx, sy+dy);
                                int r = (color >> 16) & 0xFF;
                                int g = (color >> 8) & 0xFF;
                                int b = color & 0xFF;

                                // commented out version might be faster, but needs to be tested as gives slightly different results due to numerical wobble
                                /*float fr = r, fg = g, fb = b;
                                float weight = pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                fr *= weight;
                                fg *= weight;
                                fb *= weight;*/
                                float fr = ((float)r) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                float fg = ((float)g) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                float fb = ((float)b) * pyramid_blending_weights[2+dx] * pyramid_blending_weights[2+dy];
                                sum_fr += fr;
                                sum_fg += fg;
                                sum_fb += fb;
                            }
                        }

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    else {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        //int color = bitmap_in.getPixel(sx, sy);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapXFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapXFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                fast_bitmap_in[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {

                    int sx = 2*x;

                    if( sx >= 2 && sx < width-2 ) {

                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx+dx)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loops

                        int offset = (y_rel_bitmap_in_cache)*width+(sx);
                        int color;

                        color = bitmap_in_cache_pixels[offset-2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[offset-1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[offset];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[offset+1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[offset+2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    else {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        //int color = bitmap_in.getPixel(sx, y);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapYFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ReduceBitmapYFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                int sy = 2*y;

                fast_bitmap_in[thread_index].ensureCache(sy-2, sy+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                if( sy >= 2 & sy < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loops

                        int color;

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        //int color = bitmap_in.getPixel(x, sy);
                        pixels_out[c] = color;
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapXFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width; // width of bitmap_out (bitmap_in should be twice the width)

        ReduceBitmapXFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                for(int x=off_x;x<off_x+this_width;x++,c+=4) {

                    int sx = 2*x;
                    int pixel_index = 4*((y)*(2*width)+(sx));

                    if( sx >= 2 && sx < (2*width)-2 ) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx+dx)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loops

                        int offset;

                        offset = pixel_index-8;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[0];

                        offset = pixel_index-4;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[1];

                        offset = pixel_index;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[2];

                        offset = pixel_index+4;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[3];

                        offset = pixel_index+8;
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                    else {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[pixel_index+1];
                        bitmap_out[c+2] = bitmap_in[pixel_index+2];
                        bitmap_out[c+3] = bitmap_in[pixel_index+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ReduceBitmapYFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width; // width of bitmap_out (bitmap_in should be the same width)
        private final int height; // width of bitmap_out (bitmap_in should be twice the height)

        ReduceBitmapYFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array

                int sy = 2*y;

                if( sy >= 2 & sy < (2*height)-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loops

                        int offset;

                        offset = 4*((sy-2)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[0];

                        offset = 4*((sy-1)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[1];

                        offset = 4*((sy)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[2];

                        offset = 4*((sy+1)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[3];

                        offset = 4*((sy+2)*(width)+(x));
                        sum_fr += ((float)(bitmap_in[offset+1] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)(bitmap_in[offset+2] & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(bitmap_in[offset+3] & 0xFF)) * pyramid_blending_weights[4];

                        // end unroll loops

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        /*r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));*/

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        int pixel_index = 4*((sy)*(width)+(x));
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[pixel_index+1];
                        bitmap_out[c+2] = bitmap_in[pixel_index+2];
                        bitmap_out[c+3] = bitmap_in[pixel_index+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class ExpandBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        ExpandBitmapFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {

                if( y % 2 == 0 ) {
                    int sy = y/2;

                    fast_bitmap_in[thread_index].ensureCache(sy, sy); // force cache to cover rows needed by this row
                    int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                    int y_rel_bitmap_in_cache = sy-bitmap_in_cache_y;
                    int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        if( x % 2 == 0 ) {
                            int sx = x/2;
                            pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(sx)];
                        }
                        else {
                            pixels_out[c] = (255 << 24);
                        }
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = (255 << 24);
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), rather than being a general blur
     *  function.
     */
    static class Blur1dXFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        Blur1dXFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                if( y % 2 == 1 ) {
                    // can skip odd y lines, as will be all zeroes (due to the result of ExpandBitmapFunction)
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = (255 << 24);
                    }
                    continue;
                }

                fast_bitmap_in[thread_index].ensureCache(y, y); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                int sx = Math.max(off_x, 2);
                int ex = Math.min(off_x+this_width, width-2);

                for(int x=off_x;x<sx;x++,c++) {
                    // x values < 2
                    pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                }

                //for(int x=off_x;x<off_x+this_width;x++,c++) {
                for(int x=sx;x<ex;x++,c++) {
                    //if( x >= 2 && x < width-2 )
                    {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x+dx)];
                            int r = (color >> 16) & 0xFF;
                            int g = (color >> 8) & 0xFF;
                            int b = color & 0xFF;

                            float fr = ((float)r) * pyramid_blending_weights[2+dx];
                            float fg = ((float)g) * pyramid_blending_weights[2+dx];
                            float fb = ((float)b) * pyramid_blending_weights[2+dx];
                            sum_fr += fr;
                            sum_fg += fg;
                            sum_fb += fb;
                        }*/

                        // unroll loop

                        int color;
                        int pixel_index = (y_rel_bitmap_in_cache)*width+x;

                        // when blending, we can take advantage of the fact that pixels will be 0 at odd x coordinates (due to the result of ExpandBitmapFunction)
                        if( x % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            color = bitmap_in_cache_pixels[pixel_index-1];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            color = bitmap_in_cache_pixels[pixel_index+1];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            color = bitmap_in_cache_pixels[pixel_index-2];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            color = bitmap_in_cache_pixels[pixel_index];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            color = bitmap_in_cache_pixels[pixel_index+2];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        }
                        /*
                        color = bitmap_in_cache_pixels[pixel_index-2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[pixel_index-1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[pixel_index];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[pixel_index+1];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[pixel_index+2];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        */

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                    /*else {
                        pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                    }*/
                }

                for(int x=ex;x<off_x+this_width;x++,c++) {
                    // x values >= width-2
                    pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), that was then processed with
     *  Blur1dXFunction, rather than being a general blur function.
     */
    static class Blur1dYFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap_in;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap_in;

        Blur1dYFunction(Bitmap bitmap_in) {
            this.bitmap_in = bitmap_in;
            this.width = bitmap_in.getWidth();
            this.height = bitmap_in.getHeight();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap_in = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap_in[i] = new JavaImageProcessing.FastAccessBitmap(bitmap_in);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                fast_bitmap_in[thread_index].ensureCache(y-2, y+2); // force cache to cover rows needed by this row
                int bitmap_in_cache_y = fast_bitmap_in[thread_index].getCacheY();
                int y_rel_bitmap_in_cache = y-bitmap_in_cache_y;
                int [] bitmap_in_cache_pixels = fast_bitmap_in[thread_index].getCachedPixelsI();

                if( y >= 2 && y < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+dy)*width+(x)];
                            int r = (color >> 16) & 0xFF;
                            int g = (color >> 8) & 0xFF;
                            int b = color & 0xFF;

                            float fr = ((float)r) * pyramid_blending_weights[2+dy];
                            float fg = ((float)g) * pyramid_blending_weights[2+dy];
                            float fb = ((float)b) * pyramid_blending_weights[2+dy];
                            sum_fr += fr;
                            sum_fg += fg;
                            sum_fb += fb;
                        }*/

                        // unroll loop:

                        int color;

                        // when blending, due to having blurred X the result of ExpandBitmapFunction, we will now have odd-y lines being zero, even-y lines being non-zero
                        if( y % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                            sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        }

                        /*
                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[0];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[0];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache-1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[1];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[1];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[2];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[2];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+1)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[3];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[3];

                        color = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache+2)*width+(x)];
                        sum_fr += ((float)((color >> 16) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fg += ((float)((color >> 8) & 0xFF)) * pyramid_blending_weights[4];
                        sum_fb += ((float)(color & 0xFF)) * pyramid_blending_weights[4];
                        */

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        r = Math.max(0, Math.min(255, r));
                        g = Math.max(0, Math.min(255, g));
                        b = Math.max(0, Math.min(255, b));

                        // this code is performance critical; note it's faster to avoid calls to Color.argb()
                        pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c++) {
                        pixels_out[c] = bitmap_in_cache_pixels[(y_rel_bitmap_in_cache)*width+(x)];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Alpha isn't written on result for performance.
     */
    static class ExpandBitmapFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, /** @noinspection FieldCanBeLocal*/ height; // dimensions of bitmap_out (bitmap_in should be half the width and half the height)

        ExpandBitmapFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array

                if( y % 2 == 0 ) {
                    int sy = y/2;

                    /*for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        if( x % 2 == 0 ) {
                            int sx = x/2;
                            int sc = 4*(sy*(width/2)+sx); // index into bitmap_in array (n.b., width/2 as bitmap_in is half the size)
                            bitmap_out[c] = bitmap_in[sc];
                            bitmap_out[c+1] = bitmap_in[sc+1];
                            bitmap_out[c+2] = bitmap_in[sc+2];
                            bitmap_out[c+3] = bitmap_in[sc+3];
                        }
                        else {
                            bitmap_out[c] = (byte)255;
                        }
                    }*/
                    // copy even x (assumes off_x is even)
                    //int saved_c = c;
                    for(int sx=off_x/2;sx<(off_x+this_width)/2;sx++,c+=8) {
                        int sc = 4*(sy*(width/2)+sx); // index into bitmap_in array (n.b., width/2 as bitmap_in is half the size)
                        //bitmap_out[c] = bitmap_in[sc];
                        bitmap_out[c+1] = bitmap_in[sc+1];
                        bitmap_out[c+2] = bitmap_in[sc+2];
                        bitmap_out[c+3] = bitmap_in[sc+3];
                    }
                    // skip writing odd x
                    /*
                    // copy odd x
                    c = saved_c+4;
                    for(int x=off_x+1;x<off_x+this_width;x+=2,c+=8) {
                        bitmap_out[c] = (byte)255;
                    }
                   */
                }
                /*else {
                    // skip writing odd y
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                    }
                }*/
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), rather than being a general blur
     *  function.
     *  Alpha isn't written on result for performance.
     */
    static class Blur1dXFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, /** @noinspection FieldCanBeLocal*/ height;

        Blur1dXFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                if( y % 2 == 1 ) {
                    // can skip odd y lines, as will be all zeroes (due to the result of ExpandBitmapFunction)
                    /*for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                    }*/
                    continue;
                }

                int sx = Math.max(off_x, 2);
                int ex = Math.min(off_x+this_width, width-2);

                for(int x=off_x;x<sx;x++,c+=4) {
                    // x values < 2
                    //bitmap_out[c] = bitmap_in[c];
                    bitmap_out[c+1] = bitmap_in[c+1];
                    bitmap_out[c+2] = bitmap_in[c+2];
                    bitmap_out[c+3] = bitmap_in[c+3];
                }

                //for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                for(int x=sx;x<ex;x++,c+=4) {
                    //if( x >= 2 && x < width-2 )
                    {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dx=-2;dx<=2;dx++) {
                            int index = 4*((y)*width+(x+dx));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2+dx];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2+dx];
                        }*/

                        // unroll loop

                        int pixel_index = 4*((y)*width+(x)), index;

                        // when blending, we can take advantage of the fact that pixels will be 0 at odd x coordinates (due to the result of ExpandBitmapFunction)
                        if( x % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            index = pixel_index-4;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            index = pixel_index+4;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            index = pixel_index-8;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            index = pixel_index;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            index = pixel_index+8;
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[4];
                        }

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        //r = Math.max(0, Math.min(255, r));
                        //g = Math.max(0, Math.min(255, g));
                        //b = Math.max(0, Math.min(255, b));

                        //bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                    /*else {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[c+1];
                        bitmap_out[c+2] = bitmap_in[c+2];
                        bitmap_out[c+3] = bitmap_in[c+3];
                    }*/
                }

                for(int x=ex;x<off_x+this_width;x++,c+=4) {
                    // x values >= width-2
                    //bitmap_out[c] = bitmap_in[c];
                    bitmap_out[c+1] = bitmap_in[c+1];
                    bitmap_out[c+2] = bitmap_in[c+2];
                    bitmap_out[c+3] = bitmap_in[c+3];
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    /** Note that this is optimised for being called on a result of ExpandBitmapFunction (where only
     *  the top-left pixel in each group of 2x2 will be non-zero), that was then processed with
     *  Blur1dXFunction, rather than being a general blur function.
     *  Alpha isn't written as 255, rather than being based on input alpha.
     */
    static class Blur1dYFullFunction implements JavaImageProcessing.ApplyFunctionInterface {
        // bitmaps in ARGB format
        private final byte [] bitmap_in;
        private final byte [] bitmap_out;
        private final int width, height;

        Blur1dYFullFunction(byte [] bitmap_in, byte [] bitmap_out, int width, int height) {
            this.bitmap_in = bitmap_in;
            this.bitmap_out = bitmap_out;
            this.width = width;
            this.height = height;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int c = 4*(y*width+off_x); // index into bitmap_out array
                if( y >= 2 && y < height-2 ) {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        float sum_fr = 0.0f;
                        float sum_fg = 0.0f;
                        float sum_fb = 0.0f;

                        /*for(int dy=-2;dy<=2;dy++) {
                            int index = 4*((y+dy)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2+dy];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2+dy];
                        }*/

                        // unroll loop:

                        int index;

                        // when blending, due to having blurred X the result of ExpandBitmapFunction, we will now have odd-y lines being zero, even-y lines being non-zero
                        if( y % 2 == 1 ) {
                            // odd coordinate: so only immediately adjacent coordinates will be non-0

                            // pixel_index-2 is zero

                            index = 4*((y-1)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[1];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[1];

                            // pixel_index is zero

                            index = 4*((y+1)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[3];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[3];

                            // pixel_index+2 is zero
                        }
                        else {
                            // even coordinate: so adjacent coordinates will be 0
                            index = 4*((y-2)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[0];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[0];

                            // pixel_index-1 is zero

                            index = 4*((y)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[2];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[2];

                            // pixel_index+1 is zero

                            index = 4*((y+2)*width+(x));
                            sum_fr += ((float)(bitmap_in[index+1] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fg += ((float)(bitmap_in[index+2] & 0xFF)) * pyramid_blending_weights[4];
                            sum_fb += ((float)(bitmap_in[index+3] & 0xFF)) * pyramid_blending_weights[4];
                        }

                        // end unrolled loop

                        sum_fr *= 2.0f;
                        sum_fg *= 2.0f;
                        sum_fb *= 2.0f;

                        int r = (int)(sum_fr+0.5f);
                        int g = (int)(sum_fg+0.5f);
                        int b = (int)(sum_fb+0.5f);

                        //r = Math.max(0, Math.min(255, r));
                        //g = Math.max(0, Math.min(255, g));
                        //b = Math.max(0, Math.min(255, b));

                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = (byte)r;
                        bitmap_out[c+2] = (byte)g;
                        bitmap_out[c+3] = (byte)b;
                    }
                }
                else {
                    for(int x=off_x;x<off_x+this_width;x++,c+=4) {
                        bitmap_out[c] = (byte)255;
                        bitmap_out[c+1] = bitmap_in[c+1];
                        bitmap_out[c+2] = bitmap_in[c+2];
                        bitmap_out[c+3] = bitmap_in[c+3];
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class SubtractBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf; // output
        private final Bitmap bitmap1;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final int width;

        SubtractBitmapFunction(float [] pixels_rgbf, Bitmap bitmap1) {
            this.pixels_rgbf = pixels_rgbf;
            this.bitmap1 = bitmap1;
            this.width = bitmap1.getWidth();
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;

                fast_bitmap1[thread_index].getPixel(0, y); // force cache to cover row y
                int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    //int color1 = fast_bitmap1[thread_index].getPixel(x, y);
                    int color1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache)*width+(x)];
                    float pixel1_fr = (float)((color1 >> 16) & 0xFF);
                    float pixel1_fg = (float)((color1 >> 8) & 0xFF);
                    float pixel1_fb = (float)(color1 & 0xFF);

                    float fr = pixel0_fr - pixel1_fr;
                    float fg = pixel0_fg - pixel1_fg;
                    float fb = pixel0_fb - pixel1_fb;

                    this.pixels_rgbf[pixels_rgbf_indx] = fr;
                    this.pixels_rgbf[pixels_rgbf_indx+1] = fg;
                    this.pixels_rgbf[pixels_rgbf_indx+2] = fb;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class MergefFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf0; // input
        private final float [] pixels_rgbf1; // input
        private final int width;
        private final int [] interpolated_best_path;
        private final int merge_blend_width;
        //private final int start_blend_x;

        MergefFunction(float [] pixels_rgbf0, float [] pixels_rgbf1, int blend_width, int width, int [] interpolated_best_path) {
            this.pixels_rgbf0 = pixels_rgbf0;
            this.pixels_rgbf1 = pixels_rgbf1;
            this.width = width;
            this.interpolated_best_path = interpolated_best_path;

            merge_blend_width = blend_width;
            //start_blend_x = (full_width - merge_blend_width)/2;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            for(int y=off_y;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;
                int mid_x = interpolated_best_path[y];

                for(int x=off_x;x<off_x+this_width;x++,pixels_rgbf_indx+=3) {
                    float pixel0_fr = pixels_rgbf0[pixels_rgbf_indx];
                    float pixel0_fg = pixels_rgbf0[pixels_rgbf_indx+1];
                    float pixel0_fb = pixels_rgbf0[pixels_rgbf_indx+2];
                    float pixel1_fr = pixels_rgbf1[pixels_rgbf_indx];
                    float pixel1_fg = pixels_rgbf1[pixels_rgbf_indx+1];
                    float pixel1_fb = pixels_rgbf1[pixels_rgbf_indx+2];

                    float alpha = ((float)( x-(mid_x-merge_blend_width/2) )) / (float)merge_blend_width;
                    alpha = Math.max(alpha, 0.0f);
                    alpha = Math.min(alpha, 1.0f);

                    this.pixels_rgbf0[pixels_rgbf_indx] = (1.0f-alpha)*pixel0_fr + alpha*pixel1_fr;
                    this.pixels_rgbf0[pixels_rgbf_indx+1] = (1.0f-alpha)*pixel0_fg + alpha*pixel1_fg;
                    this.pixels_rgbf0[pixels_rgbf_indx+2] = (1.0f-alpha)*pixel0_fb + alpha*pixel1_fb;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class MergeFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap1;
        private final int width;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap1;
        private final int [] interpolated_best_path;
        private final int merge_blend_width;
        //private final int start_blend_x;

        MergeFunction(Bitmap bitmap1, int blend_width, int [] interpolated_best_path) {
            this.bitmap1 = bitmap1;
            this.width = bitmap1.getWidth();
            this.interpolated_best_path = interpolated_best_path;

            merge_blend_width = blend_width;
            //start_blend_x = (full_width - merge_blend_width)/2;
        }

        @Override
        public void init(int n_threads) {
            fast_bitmap1 = new JavaImageProcessing.FastAccessBitmap[n_threads];

            for(int i=0;i<n_threads;i++) {
                fast_bitmap1[i] = new JavaImageProcessing.FastAccessBitmap(bitmap1);
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int mid_x = interpolated_best_path[y];

                fast_bitmap1[thread_index].getPixel(0, y); // force cache to cover row y
                int bitmap1_cache_y = fast_bitmap1[thread_index].getCacheY();
                int y_rel_bitmap1_cache = y-bitmap1_cache_y;
                int [] bitmap1_cache_pixels = fast_bitmap1[thread_index].getCachedPixelsI();

                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    //int color1 = fast_bitmap1[thread_index].getPixel(x, y);
                    int color1 = bitmap1_cache_pixels[(y_rel_bitmap1_cache)*width+(x)];
                    float pixel1_fr = (float)((color1 >> 16) & 0xFF);
                    float pixel1_fg = (float)((color1 >> 8) & 0xFF);
                    float pixel1_fb = (float)(color1 & 0xFF);

                    float alpha = ((float)( x-(mid_x-merge_blend_width/2) )) / (float)merge_blend_width;
                    alpha = Math.max(alpha, 0.0f);
                    alpha = Math.min(alpha, 1.0f);

                    float fr = (1.0f-alpha)*pixel0_fr + alpha*pixel1_fr;
                    float fg = (1.0f-alpha)*pixel0_fg + alpha*pixel1_fg;
                    float fb = (1.0f-alpha)*pixel0_fb + alpha*pixel1_fb;

                    int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    static class AddBitmapFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final float [] pixels_rgbf1;
        private final int width;

        AddBitmapFunction(float [] pixels_rgbf1, int width) {
            this.pixels_rgbf1 = pixels_rgbf1;
            this.width = width;
        }

        @Override
        public void init(int n_threads) {
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, int [] pixels, int off_x, int off_y, int this_width, int this_height) {
            int [] pixels_out = output.getCachedPixelsI();

            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                int pixels_rgbf_indx = 3*y*width;

                for(int x=off_x;x<off_x+this_width;x++,c++,pixels_rgbf_indx+=3) {
                    // this code is performance critical; note it's faster to avoid calls to Color.red/green/blue()
                    int color0 = pixels[c];
                    float pixel0_fr = (float)((color0 >> 16) & 0xFF);
                    float pixel0_fg = (float)((color0 >> 8) & 0xFF);
                    float pixel0_fb = (float)(color0 & 0xFF);

                    float pixel1_fr = pixels_rgbf1[pixels_rgbf_indx];
                    float pixel1_fg = pixels_rgbf1[pixels_rgbf_indx+1];
                    float pixel1_fb = pixels_rgbf1[pixels_rgbf_indx+2];

                    float fr = pixel0_fr + pixel1_fr;
                    float fg = pixel0_fg + pixel1_fg;
                    float fb = pixel0_fb + pixel1_fb;

                    int r = (int)(fr+0.5f);
                    int g = (int)(fg+0.5f);
                    int b = (int)(fb+0.5f);

                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));

                    // this code is performance critical; note it's faster to avoid calls to Color.argb()
                    pixels_out[c] = (255 << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }
}
