// JNI bridge to whisper.cpp for com.whatchapp.hourlybuzz.recite.WhisperLib.
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "whisper.h"

#define JNI_FN(name) Java_com_whatchapp_hourlybuzz_recite_WhisperLib_##name

JNIEXPORT jlong JNICALL JNI_FN(init)(JNIEnv *env, jclass clazz, jstring model_path) {
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL JNI_FN(free)(JNIEnv *env, jclass clazz, jlong ctx) {
    if (ctx) whisper_free((struct whisper_context *) ctx);
}

// Transcribes 16 kHz mono audio. Tokens can split a UTF-8 character, so the
// result is raw bytes: for each token, a 4-byte float probability (little
// endian), a 2-byte length and the token's bytes. Kotlin rebuilds the words.
JNIEXPORT jbyteArray JNICALL JNI_FN(transcribe)(JNIEnv *env, jclass clazz, jlong ctx_ptr,
                                                jfloatArray audio, jint threads, jint audio_ctx) {
    struct whisper_context *ctx = (struct whisper_context *) ctx_ptr;
    if (!ctx) return (*env)->NewByteArray(env, 0);

    jsize n = (*env)->GetArrayLength(env, audio);
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);

    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language = "ar";
    p.translate = false;
    p.n_threads = threads;
    p.no_context = true;
    p.no_timestamps = true;
    p.single_segment = true;
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.suppress_blank = true;
    p.temperature = 0.0f;
    p.temperature_inc = 0.0f; // no retries: keep latency predictable
    if (audio_ctx > 0) p.audio_ctx = audio_ctx;

    int rc = whisper_full(ctx, p, samples, n);
    (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);
    if (rc != 0) return (*env)->NewByteArray(env, 0);

    size_t cap = 4096, len = 0;
    unsigned char *out = malloc(cap);
    const whisper_token eot = whisper_token_eot(ctx);
    for (int s = 0; s < whisper_full_n_segments(ctx); s++) {
        for (int t = 0; t < whisper_full_n_tokens(ctx, s); t++) {
            whisper_token id = whisper_full_get_token_id(ctx, s, t);
            if (id >= eot) continue; // special tokens
            const char *text = whisper_full_get_token_text(ctx, s, t);
            float prob = whisper_full_get_token_p(ctx, s, t);
            size_t tl = strlen(text);
            if (tl > 65535) tl = 65535;
            if (len + tl + 6 > cap) {
                cap = (len + tl + 6) * 2;
                out = realloc(out, cap);
            }
            memcpy(out + len, &prob, 4);
            out[len + 4] = (unsigned char) (tl & 0xff);
            out[len + 5] = (unsigned char) (tl >> 8);
            memcpy(out + len + 6, text, tl);
            len += tl + 6;
        }
    }
    jbyteArray result = (*env)->NewByteArray(env, (jsize) len);
    (*env)->SetByteArrayRegion(env, result, 0, (jsize) len, (const jbyte *) out);
    free(out);
    return result;
}
