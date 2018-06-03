package com.doctoror.particlesdrawable.renderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

import com.doctoror.particlesdrawable.ParticlesScene;
import com.doctoror.particlesdrawable.util.GLESUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

final class GlSceneRendererParticles {

    private static final int BYTES_PER_SHORT = 2;
    private static final int COORDINATES_PER_VERTEX = 2;
    private static final int VERTICES_PER_PARTICLE = 6;

    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private final int[] textureHandle = new int[1];

    private ShortBuffer particlesTrianglesCoordinates;
    private ByteBuffer particlesTexturesCoordinates;

    private volatile boolean textureDirty;

    private int program;

    GlSceneRendererParticles() {
        // prepare shaders and OpenGL program
        final int vertexShader = GLESUtils.loadShader(
                GLES30.GL_VERTEX_SHADER,
                VERTEX_SHADER_CODE);

        final int fragmentShader = GLESUtils.loadShader(
                GLES30.GL_FRAGMENT_SHADER,
                FRAGMENT_SHADER_CODE);

        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        GLES30.glLinkProgram(program);
    }

    void markTextureDirty() {
        textureDirty = true;
    }

    void initBuffers(final int vertexCount) {
        initParticleTrianglesBuffer(vertexCount);
        initParticleTexturesBuffer(vertexCount);
    }

    void recycle(@NonNull final GL10 gl) {
        gl.glDeleteTextures(1, textureHandle, 0);
    }

    private void initParticleTrianglesBuffer(final int vertexCount) {
        final int floatcapacity = vertexCount * COORDINATES_PER_VERTEX * VERTICES_PER_PARTICLE;
        if (particlesTrianglesCoordinates == null
                || particlesTrianglesCoordinates.capacity() != floatcapacity) {
            final ByteBuffer triangleShit = ByteBuffer.allocateDirect(floatcapacity * BYTES_PER_SHORT);
            triangleShit.order(ByteOrder.nativeOrder());
            particlesTrianglesCoordinates = triangleShit.asShortBuffer();
        }
    }

    private void initParticleTexturesBuffer(final int vertexCount) {
        final int capacity = vertexCount * COORDINATES_PER_VERTEX * VERTICES_PER_PARTICLE;
        if (particlesTexturesCoordinates == null
                || particlesTexturesCoordinates.capacity() != capacity) {
            particlesTexturesCoordinates = ByteBuffer.allocateDirect(capacity);
            particlesTexturesCoordinates.order(ByteOrder.nativeOrder());

            for (int i = 0; i < capacity; i += VERTICES_PER_PARTICLE) {
                particlesTexturesCoordinates.put((byte) 0);
                particlesTexturesCoordinates.put((byte) 1);
                particlesTexturesCoordinates.put((byte) 0);
                particlesTexturesCoordinates.put((byte) 0);
                particlesTexturesCoordinates.put((byte) 1);
                particlesTexturesCoordinates.put((byte) 0);
            }
        }
    }

    private void generateAndLoadTexture(
            @ColorInt final int color,
            final float maxParticleRadius) {
        final Bitmap texture = generateParticleTexture(color, maxParticleRadius);
        //loadTexture(gl, texture);
        texture.recycle();
    }

    @NonNull
    private Bitmap generateParticleTexture(
            @ColorInt final int color,
            final float maxPointRadius) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(color);

        final int size = (int) (maxPointRadius * 2f);
        final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawCircle(maxPointRadius, maxPointRadius, maxPointRadius, paint);

        return bitmap;
    }

    private void reloadTextureIfDirty(
            @ColorInt final int color,
            final float maxParticleRadius) {
        if (textureDirty) {
            generateAndLoadTexture(color, maxParticleRadius);
        }
    }

    private void loadTexture(
            @NonNull final GL10 gl,
            @NonNull final Bitmap texture) {
        gl.glDeleteTextures(1, textureHandle, 0);

        gl.glGenTextures(1, textureHandle, 0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureHandle[0]);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);

        textureDirty = false;
    }

    public void drawScene(@NonNull final float[] mvpMatrix, @NonNull final ParticlesScene scene) {
        reloadTextureIfDirty(scene.getDotColor(), scene.getMaxDotRadius());
        resolveParticleTriangles(scene);
        drawParticles(mvpMatrix, scene.getNumDots());
    }

    private void resolveParticleTriangles(@NonNull final ParticlesScene scene) {
        final FloatBuffer coordinates = scene.getCoordinates();
        coordinates.position(0);

        final FloatBuffer radiuses = scene.getRadiuses();
        radiuses.position(0);

        particlesTrianglesCoordinates.clear();

        final int count = scene.getNumDots();
        for (int i = 0; i < count; i++) {
            final float particleRadius = radiuses.get();

            final float coordX = coordinates.get() - particleRadius;
            final float coordY = coordinates.get() - particleRadius;

            final float particleSize = particleRadius * 2f;
            particlesTrianglesCoordinates.put((short) coordX);
            particlesTrianglesCoordinates.put((short) coordY);
            particlesTrianglesCoordinates.put((short) (coordX + particleSize));
            particlesTrianglesCoordinates.put((short) coordY);
            particlesTrianglesCoordinates.put((short) (coordX + particleSize));
            particlesTrianglesCoordinates.put((short) (coordY + particleSize));

            particlesTrianglesCoordinates.put((short) coordX);
            particlesTrianglesCoordinates.put((short) coordY);
            particlesTrianglesCoordinates.put((short) coordX);
            particlesTrianglesCoordinates.put((short) (coordY + particleSize));
            particlesTrianglesCoordinates.put((short) (coordX + particleSize));
            particlesTrianglesCoordinates.put((short) (coordY + particleSize));
        }
    }

    // TODO remove
    private final float color[] = {1f, 1f, 1f, 1f};

    private void drawParticles(@NonNull final float[] mvpMatrix, final int count) {
        GLES30.glUseProgram(program);

        final int positionHandle = GLES30.glGetAttribLocation(program, "vPosition");
        GLESUtils.checkGlError("glGetAttribLocation");

        GLES30.glEnableVertexAttribArray(positionHandle);
        GLESUtils.checkGlError("glEnableVertexAttribArray");

        particlesTexturesCoordinates.position(0);
        particlesTrianglesCoordinates.position(0);

        GLES30.glVertexAttribPointer(
                positionHandle,
                COORDINATES_PER_VERTEX,
                GLES30.GL_FLOAT,
                false,
                0,
                particlesTrianglesCoordinates);

        GLESUtils.checkGlError("glVertexAttribPointer");

        final int colorHandle = GLES30.glGetUniformLocation(program, "vColor");
        GLESUtils.checkGlError("glGetUniformLocation");

        GLES30.glUniform4fv(colorHandle, 1, color, 0);

        final int mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix");
        GLESUtils.checkGlError("glGetUniformLocation");

        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLESUtils.checkGlError("glUniformMatrix4fv");

        GLES30.glDrawArrays(GL10.GL_TRIANGLES, 0, count * VERTICES_PER_PARTICLE);
        GLESUtils.checkGlError("glDrawArrays");

        GLES30.glDisableVertexAttribArray(positionHandle);
    }
}
