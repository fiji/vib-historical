#include "fastpng_Native_PNG_Writer.h"

#include <stdio.h>
#include <stdlib.h>

#include "png.h"

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

/*
 * Class:     fastpng_Native_PNG_Writer
 * Method:    write8BitPNG
 * Signature: ([BII[B[B[BLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_fastpng_Native_1PNG_1Writer_write8BitPNG
  (JNIEnv * env,
   jobject this,
   jbyteArray pixel_data_java,
   jint width,
   jint height,
   jbyteArray reds_java,
   jbyteArray greens_java,
   jbyteArray blues_java,
   jstring output_filename_java)
{
    char error_buffer[ERROR_BUFFER_SIZE];

    int reds_length = -1;
    int greens_length = -1;
    int blues_length = -1;

    signed char * reds = NULL;
    signed char * greens = NULL;
    signed char * blues = NULL;

    int required_pixel_data_length = width * height;
    int pixel_data_length = -1;

    signed char * pixel_data = NULL;

    const jbyte * output_filename = (*env)->GetStringUTFChars(env,output_filename_java,NULL);
    if( ! output_filename )
        RUNTIME_EXCEPTION("Couldn't allocate space for String");

    if( ! pixel_data_java )
        RUNTIME_EXCEPTION("No pixel data supplied");

    pixel_data_length = (*env)->GetArrayLength(env,pixel_data_java);

    if( required_pixel_data_length != pixel_data_length )
        RUNTIME_EXCEPTION("Length of pixel data didn't match dimensions");

    pixel_data = malloc(pixel_data_length);
    if( ! pixel_data )
        RUNTIME_EXCEPTION("Couldn't allocate space for pixel data");

    printf("Hello, output filename is %s\n",output_filename);

    // Now copy the pixel data:
    (*env)->GetByteArrayRegion(env,pixel_data_java,0,pixel_data_length,pixel_data);

    if( reds_java ) {
        reds_length = (*env)->GetArrayLength(env,reds_java);
        reds = malloc(reds_length);
        if( ! reds )
            RUNTIME_EXCEPTION("Couldn't allocate space for reds of palette");
        (*env)->GetByteArrayRegion(env,reds_java,0,reds_length,reds);
    }

    if( greens_java ) {
        greens_length = (*env)->GetArrayLength(env,greens_java);
        greens = malloc(greens_length);
        if( ! greens )
            RUNTIME_EXCEPTION("Couldn't allocate space for greens of palette");
        (*env)->GetByteArrayRegion(env,greens_java,0,greens_length,greens);
    }

    if( blues_java ) {
        blues_length = (*env)->GetArrayLength(env,blues_java);
        blues = malloc(blues_length);
        if( ! blues )
            RUNTIME_EXCEPTION("Couldn't allocate space for blues of palette");
        (*env)->GetByteArrayRegion(env,blues_java,0,blues_length,blues);
    }

    if( ! ( (reds_length == greens_length) &&
            (greens_length == blues_length) ) )
        RUNTIME_EXCEPTION("Red, green and blue arrays must be the same size");

    {
        png_bytepp rows = NULL;
        png_infop info_ptr = NULL;
        png_structp png_ptr;
        int j = -1, k = -1;

        FILE * fp = fopen( output_filename, "wb" );
        if( ! fp ) {
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Can't open PNG file [%s] for output",output_filename);
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        png_ptr = png_create_write_struct( PNG_LIBPNG_VER_STRING, 0, 0, 0 );
        if( ! png_ptr ) {
            fclose(fp);
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to allocate a png_structp");
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        info_ptr = png_create_info_struct( png_ptr );
        if( ! info_ptr ) {
            png_destroy_write_struct( &png_ptr, 0 );
            fclose(fp);
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to allocate a png_infop");
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        png_init_io( png_ptr, fp );

        png_set_compression_level( png_ptr, 3 );

        png_set_IHDR( png_ptr,
                      info_ptr,
                      width,
                      height,
                      8,
                      (reds_length < 0) ? PNG_COLOR_TYPE_GRAY : PNG_COLOR_TYPE_PALETTE,
                      PNG_INTERLACE_NONE,
                      PNG_COMPRESSION_TYPE_DEFAULT, // The only option...
                      PNG_FILTER_TYPE_DEFAULT );

        printf("reds_length is %d\n",reds_length);

        png_color * png_palette = 0;
        if( reds_length >= 0 ) {
            png_palette = (png_color *) png_malloc(
                png_ptr,
                sizeof(png_color) * reds_length );png_set_PLTE(png_ptr, info_ptr, png_palette, reds_length);
            {
                int pi;
                for( pi = 0; pi < reds_length; ++pi ) {
                    png_palette[pi].red    = reds[pi];
                    png_palette[pi].blue   = blues[pi];
                    png_palette[pi].green  = greens[pi];
                }
            }
            printf("setting palette\n");
            png_set_PLTE(png_ptr, info_ptr, png_palette, reds_length);
        }

        png_write_info(png_ptr, info_ptr);

        if( setjmp( png_ptr->jmpbuf ) ) {
            png_destroy_write_struct( &png_ptr, 0 );
            fclose(fp);
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to setjmp");
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        int pitch = png_get_rowbytes( png_ptr, info_ptr );
        // printf( "pitch is: %d\n",pitch );

        rows = malloc( height * sizeof(png_bytep) );

        for( j = 0; j < height; ++j ) {
            unsigned char * row = malloc(pitch);
            if( ! row ) {
                int l;
                png_destroy_write_struct( &png_ptr, 0 );
                for( l = 0; l < j; ++j ) {
                    free(rows[l]);
                }
                free(rows);
                fclose(fp);
                snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to allocate space for row");
                error_buffer[ERROR_BUFFER_SIZE-1] = 0;
                RUNTIME_EXCEPTION(error_buffer);
            }
            for( k = 0; k < width; ++k ) {
                memcpy(row,pixel_data+j*width,width);
            }
            rows[j] = row;
        }

        png_write_image( png_ptr, rows );

        png_write_end( png_ptr, 0 );

        png_destroy_write_struct( &png_ptr, &info_ptr );

        for( j = 0; j < height; ++j ) {
            free(rows[j]);
        }
        free( rows );
    }

    (*env)->ReleaseStringUTFChars(env,output_filename_java,output_filename);

    return 1;
}

/*
 * Class:     fastpng_Native_PNG_Writer
 * Method:    writeFullColourPNG
 * Signature: ([IIILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_fastpng_Native_1PNG_1Writer_writeFullColourPNG
  (JNIEnv * env,
   jobject this,
   jintArray pixel_data,
   jint width,
   jint height,
   jstring output_filename_java)
{
    return 0;
}
