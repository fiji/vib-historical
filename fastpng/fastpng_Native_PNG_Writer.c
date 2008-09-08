
#include "fastpng_Native_PNG_Writer.h"

#include <stdio.h>
#include <stdlib.h>

#include "png.h"        /* libpng header; includes zlib.h */
// #include "readpng.h"    /* typedefs, common macros, public prototypes */

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
    if( ! output_filename ) {
        return 0;
    }

    if( ! pixel_data_java )
        return 0;

    pixel_data_length = (*env)->GetArrayLength(env,pixel_data_java);

    if( required_pixel_data_length != pixel_data_length )
        return 0;

    pixel_data = malloc(pixel_data_length);
    if( ! pixel_data )
        return 0;

    printf("Hello, output filename is %s\n",output_filename);

    // Now copy the pixel data:
    (*env)->GetByteArrayRegion(env,pixel_data_java,0,pixel_data_length,pixel_data);

    if( reds ) {
        reds_length = (*env)->GetArrayLength(env,reds_java);
        reds = malloc(reds_length);
        if( ! reds )
            return 0;
        (*env)->GetByteArrayRegion(env,reds_java,0,reds_length,reds);
    }

    if( greens ) {
        greens_length = (*env)->GetArrayLength(env,greens_java);
        greens = malloc(greens_length);
        if( ! greens )
            return 0;
        (*env)->GetByteArrayRegion(env,greens_java,0,greens_length,greens);
    }

    if( blues ) {
        blues_length = (*env)->GetArrayLength(env,blues_java);
        blues = malloc(blues_length);
        if( ! blues )
            return 0;
        (*env)->GetByteArrayRegion(env,blues_java,0,blues_length,blues);    
    }

    {
        png_bytepp rows = NULL;
        png_infop info_ptr = NULL;
        png_structp png_ptr;
        int j = -1, k = -1;

        FILE * fp = fopen( output_filename, "wb" );
        if( ! fp ) {
            fprintf(stderr, "Can't open PNG file [%s] for output\n",
                    output_filename);
            return -1;
        }

        png_ptr = png_create_write_struct( PNG_LIBPNG_VER_STRING, 0, 0, 0 );
        if( ! png_ptr ) {
            fprintf(stderr, "Failed to allocate a png_structp\n" );
            fclose(fp);
            return -1;
        }

        info_ptr = png_create_info_struct( png_ptr );
        if( ! info_ptr ) {
            fprintf(stderr, "Failed to allocate a png_infop" );
            png_destroy_write_struct( &png_ptr, 0 );
            fclose( fp );
            return -1;
        }
        png_init_io( png_ptr, fp );

        png_set_compression_level( png_ptr, 3 );

        png_set_IHDR( png_ptr,
                      info_ptr,
                      width,
                      height,
                      8,
                      PNG_COLOR_TYPE_GRAY,
                      PNG_INTERLACE_NONE,
                      PNG_COMPRESSION_TYPE_DEFAULT, // The only option...
                      PNG_FILTER_TYPE_DEFAULT );

        png_write_info(png_ptr, info_ptr);

        if( setjmp( png_ptr->jmpbuf ) ) {
            fprintf(stderr, "Failed to setjmp\n" );
            png_destroy_write_struct( &png_ptr, 0 );
            free(rows);
            fclose(fp);
            return -1;            
        }
        
        int pitch = png_get_rowbytes( png_ptr, info_ptr );
        // printf( "pitch is: %d\n",pitch );

        rows = malloc( height * sizeof(png_bytep) );

        for( j = 0; j < height; ++j ) {
            unsigned char * row = malloc(pitch);
            if( ! row ) {
                int l;
                fprintf(stderr, "Failed to allocate a row\n" );
                png_destroy_write_struct( &png_ptr, 0 );
                for( l = 0; l < j; ++j ) {
                    free(rows[l]);
                }
                free(rows);
                fclose(fp);
                return -1;
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
