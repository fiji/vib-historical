#include "fastjpeg_Native_JPEG_Writer.h"

#include <stdio.h>
#include <stdlib.h>

#include "jpeglib.h"
#include <setjmp.h>

#define ERROR_BUFFER_SIZE 1024

#define RUNTIME_EXCEPTION(m) { \
    jclass exceptionClass = (*env)->FindClass(env,"java/lang/RuntimeException"); \
    if( ! exceptionClass ) { \
        printf("No RuntimeException\n"); \
        return 0; \
    } \
    (*env)->ThrowNew(env, exceptionClass, m); \
    return 0; \
}

/* The following, and much of the code in this is copied from the
 * example.c file distributed with libjpeg, which can normally be
 * found in /usr/share/doc/libjpeg62-dev/examples/example.c.gz
 */

struct my_error_mgr {
    struct jpeg_error_mgr pub;/* "public" fields */
    jmp_buf setjmp_buffer;/* for return to caller */
};

typedef struct my_error_mgr * my_error_ptr;

/*
 * Here's the routine that will replace the standard error_exit method:
 */

METHODDEF(void)
my_error_exit (j_common_ptr cinfo)
{
    /* cinfo->err really points to a my_error_mgr struct, so coerce pointer */
    my_error_ptr myerr = (my_error_ptr) cinfo->err;

    /* Always display the message. */
    /* We could postpone this until after returning, if we chose. */
    (*cinfo->err->output_message) (cinfo);

    /* Return control to the setjmp point */
    longjmp(myerr->setjmp_buffer, 1);
}

/*
 * Class:     fastjpeg_Native_JPEG_Writer
 * Method:    writeFullColourJPEG
 * Signature: ([IIILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_fastjpeg_Native_1JPEG_1Writer_writeFullColourJPEG
  (JNIEnv * env,
   jobject this,
   jintArray pixel_data_java,
   jint width,
   jint height,
   jstring output_filename_java)
{
    struct jpeg_compress_struct cinfo;
    struct my_error_mgr jerr;
    FILE * outfile = NULL;
    JSAMPROW row_pointer[1];
    int row_stride = width * 3;
    unsigned int * pixel_data_int = NULL;
    int pixel_data_length = -1;
    int required_pixel_data_length = width * height;
    int j, k;

    const jbyte * output_filename = (*env)->GetStringUTFChars(env,output_filename_java,NULL);
    if( ! output_filename )
        RUNTIME_EXCEPTION("Couldn't allocate space for String");

    if( ! pixel_data_java )
        RUNTIME_EXCEPTION("Supplied pixel data was null");

    pixel_data_length = (*env)->GetArrayLength(env, pixel_data_java);

    if( required_pixel_data_length != pixel_data_length )
        RUNTIME_EXCEPTION("Length of pixel data didn't match dimensions");

    pixel_data_int = malloc(pixel_data_length * sizeof(unsigned int));
    if( ! pixel_data_int )
        RUNTIME_EXCEPTION("Couldn't allocate space for pixel data");

    (*env)->GetIntArrayRegion(env,pixel_data_java,0,pixel_data_length,pixel_data_int);

    // We have to convert to 3 bytes-per-pixel buffer:
    JSAMPLE * image_buffer = malloc(width*height*3);
    if( ! image_buffer ) {
        return 0;
    }
    // Copy over the data:
    for( j = 0; j < height; ++j ) {
        for( k = 0; k < width; ++k ) {
            unsigned int value = pixel_data_int[(j * width) + k];
            int i = (j*width+k)*3;
            image_buffer[i++] = (JSAMPLE)((value & 0xFF0000) >> 16 );
            image_buffer[i++] = (JSAMPLE)((value & 0xFF00) >> 8 );
            image_buffer[i]   = (JSAMPLE)((value & 0xFF));
        }
    }

    // Set up our error handler:
    cinfo.err = jpeg_std_error(&jerr.pub);
    jerr.pub.error_exit = my_error_exit;
    /* Establish the setjmp return context for my_error_exit to use. */
    if (setjmp(jerr.setjmp_buffer)) {
        /* If we get here, the JPEG code has signaled an error.
         * We need to clean up the JPEG object, close the input file, and return.
         */
        jpeg_destroy_decompress(&cinfo);
        if( outfile )
            fclose(outfile);
        free( image_buffer );
        free( pixel_data_int );
        (*env)->ReleaseStringUTFChars(env,output_filename_java,output_filename);
        return 0;
    }

    /* Now we can initialize the JPEG compression object. */
    jpeg_create_compress(&cinfo);

    /* Step 2: specify data destination (eg, a file) */
    /* Note: steps 2 and 3 can be done in either order. */

    /* Here we use the library-supplied code to send compressed data to a
     * stdio stream.  You can also write your own code to do something else.
     * VERY IMPORTANT: use "b" option to fopen() if you are on a machine that
     * requires it in order to write binary files.
     */
    if (!(outfile = fopen(output_filename, "wb"))) {
        fprintf(stderr, "can't open %s\n", output_filename);
        longjmp(jerr.setjmp_buffer, 1);
    }
    jpeg_stdio_dest(&cinfo, outfile);

    /* Step 3: set parameters for compression */

    /* First we supply a description of the input image.
     * Four fields of the cinfo struct must be filled in:
     */
    cinfo.image_width = width; /* image width and height, in pixels */
    cinfo.image_height = height;
    cinfo.input_components = 3;/* # of color components per pixel */
    cinfo.in_color_space = JCS_RGB; /* colorspace of input image */
    /* Now use the library's routine to set default compression parameters.
     * (You must set at least cinfo.in_color_space before calling this,
     * since the defaults depend on the source color space.)
     */
    jpeg_set_defaults(&cinfo);
    /* Now you can set any non-default parameters you wish to.
     * Here we just illustrate the use of quality (quantization table) scaling:
     */
    jpeg_set_quality(&cinfo, 85, TRUE /* limit to baseline-JPEG values */);

    /* Step 4: Start compressor */

    /* TRUE ensures that we will write a complete interchange-JPEG file.
     * Pass TRUE unless you are very sure of what you're doing.
     */
    jpeg_start_compress(&cinfo, TRUE);

    /* Step 5: while (scan lines remain to be written) */
    /*           jpeg_write_scanlines(...); */

    /* Here we use the library's state variable cinfo.next_scanline as the
     * loop counter, so that we don't have to keep track ourselves.
     * To keep things simple, we pass one scanline per call; you can pass
     * more if you wish, though.
     */

    while (cinfo.next_scanline < cinfo.image_height) {
        /* jpeg_write_scanlines expects an array of pointers to scanlines.
         * Here the array is only one element long, but you could pass
         * more than one scanline at a time if that's more convenient.
         */
        row_pointer[0] = & image_buffer[cinfo.next_scanline * row_stride];
        (void) jpeg_write_scanlines(&cinfo, row_pointer, 1);
    }

    /* Step 6: Finish compression */

    jpeg_finish_compress(&cinfo);
    /* After finish_compress, we can close the output file. */
    fclose(outfile);

    /* Step 7: release JPEG compression object */

    /* This is an important step since it will release a good deal of memory. */
    jpeg_destroy_compress(&cinfo);

    /* And we're done! */

    free( image_buffer );
    free( pixel_data_int );

    (*env)->ReleaseStringUTFChars(env,output_filename_java,output_filename);

    return 1;
}
