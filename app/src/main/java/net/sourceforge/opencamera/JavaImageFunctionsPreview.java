package net.sourceforge.opencamera;

import android.graphics.Bitmap;

public class JavaImageFunctionsPreview {
    private static final String TAG = "JavaImageFunctionsPreview";

    public static class ZebraStripesApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final int zebra_stripes_threshold;
        private final int zebra_stripes_foreground;
        private final int zebra_stripes_background;
        private final int zebra_stripes_width;

        public ZebraStripesApplyFunction(int zebra_stripes_threshold, int zebra_stripes_foreground, int zebra_stripes_background, int zebra_stripes_width) {
            this.zebra_stripes_threshold = zebra_stripes_threshold;
            this.zebra_stripes_foreground = zebra_stripes_foreground;
            this.zebra_stripes_background = zebra_stripes_background;
            this.zebra_stripes_width = zebra_stripes_width;
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

                    int value = Math.max( (color >> 16) & 0xFF, (color >> 8) & 0xFF );
                    value = Math.max( value, color & 0xFF );

                    if( value >= zebra_stripes_threshold ) {
                        int stripe = (x+y)/zebra_stripes_width;
                        if( stripe % 2 == 0 ) {
                            pixels_out[c] = zebra_stripes_background;
                        }
                        else {
                            pixels_out[c] = zebra_stripes_foreground;
                        }
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class FocusPeakingApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;

        public FocusPeakingApplyFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
            this.height = bitmap.getHeight();
        }

        @Override
        public void init(int n_threads) {
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
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int strength = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        fast_bitmap[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                        int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                        int y_rel_bitmap_cache = y-bitmap_cache_y;
                        int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                        //int pixel0c = fast_bitmap[thread_index].getPixel(x-1, y-1);
                        int pixel0c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x-1)];
                        int pixel1c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x)];
                        int pixel2c = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x+1)];
                        int pixel3c = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x-1)];
                        int pixel4c = pixels[c];
                        /*if( pixels[c] != bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)] ) {
                            throw new RuntimeException("pixel4c incorrect");
                        }*/
                        int pixel5c = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x+1)];
                        int pixel6c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x-1)];
                        int pixel7c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x)];
                        int pixel8c = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x+1)];

                        int pixel0r = (pixel0c >> 16) & 0xFF;
                        int pixel0g = (pixel0c >> 8) & 0xFF;
                        int pixel0b = pixel0c & 0xFF;

                        int pixel1r = (pixel1c >> 16) & 0xFF;
                        int pixel1g = (pixel1c >> 8) & 0xFF;
                        int pixel1b = pixel1c & 0xFF;

                        int pixel2r = (pixel2c >> 16) & 0xFF;
                        int pixel2g = (pixel2c >> 8) & 0xFF;
                        int pixel2b = pixel2c & 0xFF;

                        int pixel3r = (pixel3c >> 16) & 0xFF;
                        int pixel3g = (pixel3c >> 8) & 0xFF;
                        int pixel3b = pixel3c & 0xFF;

                        int pixel4r = (pixel4c >> 16) & 0xFF;
                        int pixel4g = (pixel4c >> 8) & 0xFF;
                        int pixel4b = pixel4c & 0xFF;

                        int pixel5r = (pixel5c >> 16) & 0xFF;
                        int pixel5g = (pixel5c >> 8) & 0xFF;
                        int pixel5b = pixel5c & 0xFF;

                        int pixel6r = (pixel6c >> 16) & 0xFF;
                        int pixel6g = (pixel6c >> 8) & 0xFF;
                        int pixel6b = pixel6c & 0xFF;

                        int pixel7r = (pixel7c >> 16) & 0xFF;
                        int pixel7g = (pixel7c >> 8) & 0xFF;
                        int pixel7b = pixel7c & 0xFF;

                        int pixel8r = (pixel8c >> 16) & 0xFF;
                        int pixel8g = (pixel8c >> 8) & 0xFF;
                        int pixel8b = pixel8c & 0xFF;

                        int value_r = ( 8*pixel4r - pixel0r - pixel1r - pixel2r - pixel3r - pixel5r - pixel6r - pixel7r - pixel8r );
                        int value_g = ( 8*pixel4g - pixel0g - pixel1g - pixel2g - pixel3g - pixel5g - pixel6g - pixel7g - pixel8g );
                        int value_b = ( 8*pixel4b - pixel0b - pixel1b - pixel2b - pixel3b - pixel5b - pixel6b - pixel7b - pixel8b );
                        strength = value_r*value_r + value_g*value_g + value_b*value_b;
                    }

                    if( strength > 256*256 ) {
                        pixels_out[c] = (255 << 24) | (255 << 16) | (255 << 8) | 255;
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
                }
            }
        }

        @Override
        public void apply(JavaImageProcessing.CachedBitmap output, int thread_index, byte [] pixels, int off_x, int off_y, int this_width, int this_height) {
            // unused
            throw new RuntimeException("not implemented");
        }
    }

    public static class FocusPeakingFilteredApplyFunction implements JavaImageProcessing.ApplyFunctionInterface {
        private final Bitmap bitmap;
        private final int width, height;
        private JavaImageProcessing.FastAccessBitmap [] fast_bitmap;

        public FocusPeakingFilteredApplyFunction(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.width = bitmap.getWidth();
            this.height = bitmap.getHeight();
        }

        @Override
        public void init(int n_threads) {
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
            int [] pixels_out = output.getCachedPixelsI();
            for(int y=off_y,c=0;y<off_y+this_height;y++) {
                for(int x=off_x;x<off_x+this_width;x++,c++) {
                    int count = 0;
                    if( x >= 1 && x < width-1 && y >= 1 && y < height-1 ) {
                        fast_bitmap[thread_index].ensureCache(y-1, y+1); // force cache to cover rows needed by this row
                        int bitmap_cache_y = fast_bitmap[thread_index].getCacheY();
                        int y_rel_bitmap_cache = y-bitmap_cache_y;
                        int [] bitmap_cache_pixels = fast_bitmap[thread_index].getCachedPixelsI();

                        // only need to read one component, as input image is now greyscale
                        int pixel1 = bitmap_cache_pixels[(y_rel_bitmap_cache-1)*width+(x)] & 0xFF;
                        int pixel3 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x-1)] & 0xFF;
                        int pixel4 = pixels[c] & 0xFF;
                        /*if( pixels[c] != bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x)] ) {
                            throw new RuntimeException("pixel4c incorrect");
                        }*/
                        int pixel5 = bitmap_cache_pixels[(y_rel_bitmap_cache)*width+(x+1)] & 0xFF;
                        int pixel7 = bitmap_cache_pixels[(y_rel_bitmap_cache+1)*width+(x)] & 0xFF;

                        if( pixel1 == 255 )
                            count++;
                        if( pixel3 == 255 )
                            count++;
                        if( pixel4 == 255 )
                            count++;
                        if( pixel5 == 255 )
                            count++;
                        if( pixel7 == 255 )
                            count++;

                    }

                    if( count >= 3 ) {
                        pixels_out[c] = (255 << 24) | (255 << 16) | (255 << 8) | 255;
                    }
                    else {
                        pixels_out[c] = 0; // transparent (zero alpha)
                    }
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
