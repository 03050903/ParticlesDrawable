package com.doctoror.particlesdrawable.renderer;

import android.graphics.Color;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.doctoror.particlesdrawable.ParticlesScene;
import com.doctoror.particlesdrawable.contract.SceneRenderer;
import com.doctoror.particlesdrawable.util.DistanceResolver;
import com.doctoror.particlesdrawable.util.LineColorResolver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

public final class GlSceneRenderer implements SceneRenderer {

    private static final int BYTES_PER_SHORT = 2;
    private static final int COORDINATES_PER_VERTEX = 2;
    private static final int COLOR_BYTES_PER_VERTEX = 4;
    private static final int VERTICES_PER_LINE = 2;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];

    private GlSceneRendererParticles particles;

    private ByteBuffer lineColorBuffer;
    private ShortBuffer lineCoordinatesBuffer;

    private int lineCount;

    private GL10 gl;

    public void markTextureDirty() {
        if (particles != null) {
            particles.markTextureDirty();
        }
    }

    public void setClearColor(
            @NonNull final GL10 gl,
            @ColorInt final int color) {
        GLES30.glClearColor(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, 0f);
    }

    public void setupGl(@NonNull final GL10 gl) {
        particles = new GlSceneRendererParticles();
        markTextureDirty();
    }

    public void setGl(@Nullable final GL10 gl) {
        this.gl = gl;
    }

    public void setDimensions(@NonNull final GL10 gl, final int width, final int height) {
        GLES30.glViewport(0, 0, width, height);

        Matrix.orthoM(mProjectionMatrix, 0, 0f, width, 0f, height, 1, -1);

        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
    }

    public void recycle(@NonNull final GL10 gl) {
        particles.recycle(gl);
    }

    private void initBuffers(final int vertexCount) {
        initLineBuffers(vertexCount);
        particles.initBuffers(vertexCount);
    }

    private void initLineBuffers(final int vertexCount) {
        final int segmentsCount = segmentsCount(vertexCount);
        initLineCoordinates(segmentsCount);
        initLineColorBuffer(segmentsCount);
        lineCount = 0;
    }

    private int segmentsCount(final int vertices) {
        return (vertices * (vertices - 1)) / 2;
    }

    private void initLineCoordinates(final int segmentsCount) {
        final int shortcapacity = segmentsCount * VERTICES_PER_LINE * COORDINATES_PER_VERTEX;
        if (lineCoordinatesBuffer == null || lineCoordinatesBuffer.capacity() != shortcapacity) {
            final ByteBuffer coordinatesByteBuffer = ByteBuffer.allocateDirect(
                    shortcapacity * BYTES_PER_SHORT);
            coordinatesByteBuffer.order(ByteOrder.nativeOrder());
            lineCoordinatesBuffer = coordinatesByteBuffer.asShortBuffer();
        }
    }

    private void initLineColorBuffer(final int lineCount) {
        final int targetCapacity = lineCount * VERTICES_PER_LINE * COLOR_BYTES_PER_VERTEX;
        if (lineColorBuffer == null || lineColorBuffer.capacity() != targetCapacity) {
            lineColorBuffer = ByteBuffer.allocateDirect(targetCapacity);
            lineColorBuffer.order(ByteOrder.nativeOrder());
        }
    }

    @Override
    public void drawScene(
            @NonNull final ParticlesScene scene) {
        GLES30.glLineWidth(scene.getLineThickness());

        initBuffers(scene.getNumDots());
        //resolveLines(scene);

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        //drawLines();
        particles.drawScene(mMVPMatrix, scene);
    }

    private void resolveLines(@NonNull final ParticlesScene scene) {
        lineColorBuffer.clear();
        lineCoordinatesBuffer.clear();

        final int particlesCount = scene.getNumDots();
        if (particlesCount != 0) {
            for (int i = 0; i < particlesCount; i++) {

                final float x1 = scene.getParticleX(i);
                final float y1 = scene.getParticleY(i);

                // Draw connection lines for eligible points
                for (int j = i + 1; j < particlesCount; j++) {

                    final float x2 = scene.getParticleX(j);
                    final float y2 = scene.getParticleY(j);

                    final float distance = DistanceResolver.distance(x1, y1, x2, y2);
                    if (distance < scene.getLineDistance()) {
                        final int lineColor = LineColorResolver.resolveLineColorWithAlpha(
                                scene.getAlpha(),
                                scene.getLineColor(),
                                scene.getLineDistance(),
                                distance);

                        resolveLine(
                                x1,
                                y1,
                                x2,
                                y2,
                                lineColor);
                    }
                }
            }
        }
    }

    private void resolveLine(
            final float startX,
            final float startY,
            final float stopX,
            final float stopY,
            @ColorInt final int color) {
        if (gl != null) {
            lineCoordinatesBuffer.put((short) startX);
            lineCoordinatesBuffer.put((short) startY);
            lineCoordinatesBuffer.put((short) stopX);
            lineCoordinatesBuffer.put((short) stopY);

            lineColorBuffer.put((byte) Color.red(color));
            lineColorBuffer.put((byte) Color.green(color));
            lineColorBuffer.put((byte) Color.blue(color));
            lineColorBuffer.put((byte) Color.alpha(color));

            lineColorBuffer.put((byte) Color.red(color));
            lineColorBuffer.put((byte) Color.green(color));
            lineColorBuffer.put((byte) Color.blue(color));
            lineColorBuffer.put((byte) Color.alpha(color));

            lineCount++;
        }
    }

    private void drawLines() {
        lineCoordinatesBuffer.position(0);
        lineColorBuffer.position(0);

        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);

        gl.glColorPointer(4, GL10.GL_UNSIGNED_BYTE, 0, lineColorBuffer);
        gl.glVertexPointer(2, GL10.GL_SHORT, 0, lineCoordinatesBuffer);
        gl.glDrawArrays(GL10.GL_LINES, 0, lineCount * 2);

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
    }
}
