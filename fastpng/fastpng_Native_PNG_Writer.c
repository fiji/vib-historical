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

#define CLEANUP \
{ \
    if( reds ) \
        free(reds); \
    if( greens ) \
        free(greens); \
    if( blues ) \
        free(blues); \
    if( pixel_data_byte ) \
        free(pixel_data_byte); \
    if( pixel_data_int ) \
        free(pixel_data_int); \
}

jboolean writePNG( JNIEnv * env,
                   jobject this,
                   jbyteArray pixel_data_byte_java,
                   jintArray pixel_data_int_java,
                   jint width,
                   jint height,
                   jbyteArray reds_java,
                   jbyteArray greens_java,
                   jbyteArray blues_java,
                   jstring output_filename_java )
{
    int isRGB;

    char error_buffer[ERROR_BUFFER_SIZE];

    int reds_length = -1;
    int greens_length = -1;
    int blues_length = -1;

    signed char * reds = NULL;
    signed char * greens = NULL;
    signed char * blues = NULL;

    int required_pixel_data_length = width * height;
    int pixel_data_length = -1;

    unsigned char * pixel_data_byte = NULL;
    unsigned int * pixel_data_int = NULL;

    const jbyte * output_filename = (*env)->GetStringUTFChars(env,output_filename_java,NULL);
    if( ! output_filename )
        RUNTIME_EXCEPTION("Couldn't allocate space for String");

    if( pixel_data_byte_java && pixel_data_int_java )
        RUNTIME_EXCEPTION("Both byte and int pixel data supplied");

    if( ! (pixel_data_byte_java || pixel_data_int_java) )
        RUNTIME_EXCEPTION("Neither byte nor int pixel data supplied");

    isRGB = pixel_data_int_java != 0;

    pixel_data_length = (*env)->GetArrayLength(env,
                                               isRGB ? pixel_data_int_java : pixel_data_byte_java);

    if( required_pixel_data_length != pixel_data_length )
        RUNTIME_EXCEPTION("Length of pixel data didn't match dimensions");

    if( isRGB ) {

        pixel_data_int = malloc(pixel_data_length * sizeof(unsigned int));
        if( ! pixel_data_int )
            RUNTIME_EXCEPTION("Couldn't allocate space for pixel data");

        (*env)->GetIntArrayRegion(env,pixel_data_int_java,0,pixel_data_length,pixel_data_int);

    } else {

        pixel_data_byte = malloc(pixel_data_length * sizeof(unsigned char));
        if( ! pixel_data_byte )
            RUNTIME_EXCEPTION("Couldn't allocate space for pixel data");

        (*env)->GetByteArrayRegion(env,pixel_data_byte_java,0,pixel_data_length,pixel_data_byte);

    }

    if( reds_java ) {
        reds_length = (*env)->GetArrayLength(env,reds_java);
        reds = malloc(reds_length);
        if( ! reds ) {
            CLEANUP;
            RUNTIME_EXCEPTION("Couldn't allocate space for reds of palette");
        }
        (*env)->GetByteArrayRegion(env,reds_java,0,reds_length,reds);
    }

    if( greens_java ) {
        greens_length = (*env)->GetArrayLength(env,greens_java);
        greens = malloc(greens_length);
        if( ! greens ) {
            CLEANUP;
            RUNTIME_EXCEPTION("Couldn't allocate space for greens of palette");
        }
        (*env)->GetByteArrayRegion(env,greens_java,0,greens_length,greens);
    }

    if( blues_java ) {
        blues_length = (*env)->GetArrayLength(env,blues_java);
        blues = malloc(blues_length);
        if( ! blues ) {
            CLEANUP;
            RUNTIME_EXCEPTION("Couldn't allocate space for blues of palette");
        }
        (*env)->GetByteArrayRegion(env,blues_java,0,blues_length,blues);
    }

    if( ! ( (reds_length == greens_length) &&
            (greens_length == blues_length) ) ) {
        CLEANUP;
        RUNTIME_EXCEPTION("Red, green and blue arrays must be the same size");
    }

    {
        png_bytepp rows = NULL;
        png_infop info_ptr = NULL;
        png_structp png_ptr;
        int j = -1, k = -1;

        FILE * fp = fopen( output_filename, "wb" );
        if( ! fp ) {
            CLEANUP;
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Can't open PNG file [%s] for output",output_filename);
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        png_ptr = png_create_write_struct( PNG_LIBPNG_VER_STRING, 0, 0, 0 );
        if( ! png_ptr ) {
            CLEANUP;
            fclose(fp);
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to allocate a png_structp");
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        info_ptr = png_create_info_struct( png_ptr );
        if( ! info_ptr ) {
            CLEANUP;
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
                      isRGB ? PNG_COLOR_TYPE_RGB :
                      ((reds_length < 0) ? PNG_COLOR_TYPE_GRAY : PNG_COLOR_TYPE_PALETTE),
                      PNG_INTERLACE_NONE,
                      PNG_COMPRESSION_TYPE_DEFAULT, // The only option...
                      PNG_FILTER_TYPE_DEFAULT );

        png_color * png_palette = 0;
        if( (! isRGB) && (reds_length >= 0) ) {
            png_palette = (png_color *) png_malloc(
                png_ptr,
                sizeof(png_color) * reds_length );
            {
                int pi;
                for( pi = 0; pi < reds_length; ++pi ) {
                    png_palette[pi].red    = reds[pi];
                    png_palette[pi].blue   = blues[pi];
                    png_palette[pi].green  = greens[pi];
                }
            }
            png_set_PLTE(png_ptr, info_ptr, png_palette, reds_length);
        }

        png_write_info(png_ptr, info_ptr);

        if( setjmp( png_ptr->jmpbuf ) ) {
            CLEANUP;
            png_destroy_write_struct( &png_ptr, 0 );
            fclose(fp);
            snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to setjmp");
            error_buffer[ERROR_BUFFER_SIZE-1] = 0;
            RUNTIME_EXCEPTION(error_buffer);
        }

        int pitch = png_get_rowbytes( png_ptr, info_ptr );

        rows = malloc( height * sizeof(png_bytep) );
        if( ! rows ) {
                CLEANUP;
                png_destroy_write_struct( &png_ptr, 0 );
                fclose(fp);
                snprintf(error_buffer,ERROR_BUFFER_SIZE-1,"Failed to allocate space for rows");
                error_buffer[ERROR_BUFFER_SIZE-1] = 0;
                RUNTIME_EXCEPTION(error_buffer);
        }

        for( j = 0; j < height; ++j ) {
            unsigned char * row = malloc(pitch);
            if( ! row ) {
                int l;
                CLEANUP;
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
            if( isRGB ) {
                for( k = 0; k < width; ++k ) {
                    unsigned int value = pixel_data_int[(j * width) + k];
                    unsigned char b = (unsigned char)( value & 0xFF );
                    unsigned char g = (unsigned char)( (value & 0xFF00) >> 8 );
                    unsigned char r = (unsigned char)( (value & 0xFF0000) >> 16 );
                    int i = k * 3;
                    row[i++] = r;
                    row[i++] = g;
                    row[i] = b;
                }
            } else {
                // With 8 bit images, it's much simpler, just memcpy the data:
                memcpy(row,pixel_data_byte+j*width,width);
            }
            rows[j] = row;
        }

        png_write_image( png_ptr, rows );

        png_write_end( png_ptr, 0 );

        fclose(fp);

        png_destroy_write_struct( &png_ptr, &info_ptr );

        for( j = 0; j < height; ++j ) {
            free(rows[j]);
        }
        free( rows );
    }

    CLEANUP;

    (*env)->ReleaseStringUTFChars(env,output_filename_java,output_filename);

    return 1;
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
    return writePNG( env,
                     this,
                     pixel_data_java,
                     NULL,
                     width,
                     height,
                     reds_java,
                     greens_java,
                     blues_java,
                     output_filename_java );
}

/*
 * Class:     fastpng_Native_PNG_Writer
 * Method:    writeFullColourPNG
 * Signature: ([IIILjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_fastpng_Native_1PNG_1Writer_writeFullColourPNG
  (JNIEnv * env,
   jobject this,
   jintArray pixel_data_java,
   jint width,
   jint height,
   jstring output_filename_java)
{
    return writePNG( env,
                     this,
                     NULL,
                     pixel_data_java,
                     width,
                     height,
                     NULL,
                     NULL,
                     NULL,
                     output_filename_java );
}
