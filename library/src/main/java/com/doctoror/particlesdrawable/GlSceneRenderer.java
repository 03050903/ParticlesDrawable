package com.doctoror.particlesdrawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLUtils;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.doctoror.particlesdrawable.contract.SceneRenderer;
import com.doctoror.particlesdrawable.util.DistanceResolver;
import com.doctoror.particlesdrawable.util.LineColorResolver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

final class GlSceneRenderer implements SceneRenderer {

    private static final int BYTES_PER_FLOAT = 4;
    private static final int COORDINATES_PER_VERTEX = 2;
    private static final int COLOR_BYTES_PER_VERTEX = 4;
    private static final int VERTICES_PER_PARTICLE = 6;
    private static final int VERTICES_PER_LINE = 2;

    private final int[] textureHandle = new int[1];

    private FloatBuffer lineCoordinatesBuffer;
    private FloatBuffer particlesTrianglesCoordinates;
    private ByteBuffer particlesTexturesCoordinates;

    private ByteBuffer lineColorBuffer;

    private int lineCount;

    private GL10 gl;

    private Bitmap particleTexture;

    private int texId;

    public void generateParticleTexture(final float maxPointRadius) {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        paint.setColor(Color.WHITE);

        final int size = (int) (maxPointRadius * 2f);
        final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawCircle(maxPointRadius, maxPointRadius, maxPointRadius, paint);

        particleTexture = bitmap;

        if (gl != null) {
            loadParticleTexture(gl, bitmap);
        }
    }

    public void setClearColor(
            @NonNull final GL10 gl,
            @ColorInt final int color) {
        gl.glClearColor(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, 0f);
    }

    public void setupGl(
            @NonNull final GL10 gl,
            final int width,
            final int height) {
        setupViewport(gl, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();

        gl.glEnable(GL10.GL_LINE_SMOOTH);
        gl.glHint(GL10.GL_LINE_SMOOTH_HINT, GL10.GL_NICEST);

        gl.glEnable(GL10.GL_BLEND);
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
        gl.glOrthof(0, width, 0, height, 1, -1);

        if (particleTexture != null) {
            loadParticleTexture(gl, particleTexture);
        }

        this.gl = gl;
    }

    private void loadParticleTexture(
            @NonNull final GL10 gl,
            @NonNull final Bitmap texture) {
        gl.glEnable(GL10.GL_TEXTURE_2D);

        gl.glGenTextures(1, textureHandle, 0);
        texId = textureHandle[0];
        gl.glBindTexture(GL10.GL_TEXTURE_2D, texId);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);
    }

    public void setupViewport(@NonNull final GL10 gl, final int width, final int height) {
        gl.glViewport(0, 0, width, height);
    }

    public void setGl(@Nullable final GL10 gl) {
        this.gl = gl;
    }

    private void initBuffers(final int vertexCount) {
        initLineBuffers(vertexCount);
        initParticleTrianglesBuffer(vertexCount);
        initParticleTexturesBuffer(vertexCount);
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
        final int floatCapacity = segmentsCount * VERTICES_PER_LINE * COORDINATES_PER_VERTEX;
        if (lineCoordinatesBuffer == null || lineCoordinatesBuffer.capacity() != floatCapacity) {
            final ByteBuffer coordinatesByteBuffer = ByteBuffer.allocateDirect(
                    floatCapacity * BYTES_PER_FLOAT);
            coordinatesByteBuffer.order(ByteOrder.nativeOrder());
            lineCoordinatesBuffer = coordinatesByteBuffer.asFloatBuffer();
        } else {
            lineCoordinatesBuffer.position(0);
        }
    }

    private void initLineColorBuffer(final int lineCount) {
        final int targetCapacity = lineCount * VERTICES_PER_LINE * COLOR_BYTES_PER_VERTEX;
        if (lineColorBuffer == null || lineColorBuffer.capacity() != targetCapacity) {
            lineColorBuffer = ByteBuffer.allocateDirect(targetCapacity);
            lineColorBuffer.order(ByteOrder.nativeOrder());
        } else {
            lineColorBuffer.position(0);
        }
    }

    private void initParticleTrianglesBuffer(final int vertexCount) {
        final int floatCapacity = vertexCount * COORDINATES_PER_VERTEX * VERTICES_PER_PARTICLE;
        if (particlesTrianglesCoordinates == null
                || particlesTrianglesCoordinates.capacity() != floatCapacity) {
            final ByteBuffer triangleShit = ByteBuffer.allocateDirect(floatCapacity * BYTES_PER_FLOAT);
            triangleShit.order(ByteOrder.nativeOrder());
            particlesTrianglesCoordinates = triangleShit.asFloatBuffer();
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

    @Override
    public void drawScene(
            @NonNull final ParticlesScene scene) {
        gl.glPointSize(scene.getMaxDotRadius() * 2f);
        gl.glLineWidth(scene.getLineThickness());

        initBuffers(scene.getParticlesCount());
        resolveLines(scene);
        resolveParticleTriangles(scene);

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        drawLines();

        drawParticles(
                scene.getDotColor(),
                scene.getParticlesCount()
        );
    }

    private void resolveLines(@NonNull final ParticlesScene scene) {
        final int particlesCount = scene.getParticlesCount();
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
            lineCoordinatesBuffer.put(startX);
            lineCoordinatesBuffer.put(startY);
            lineCoordinatesBuffer.put(stopX);
            lineCoordinatesBuffer.put(stopY);

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
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, lineCoordinatesBuffer);
        gl.glDrawArrays(GL10.GL_LINES, 0, lineCount * 2);

        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
    }

    private void resolveParticleTriangles(@NonNull final ParticlesScene scene) {
        final FloatBuffer coordinates = scene.getCoordinates();
        coordinates.position(0);

        final FloatBuffer radiuses = scene.getRadiuses();
        radiuses.position(0);

        particlesTrianglesCoordinates.position(0);

        final int count = scene.getParticlesCount();
        for (int i = 0; i < count; i++) {
            final float particleRadius = radiuses.get();
            final float particleSize = particleRadius * 2f;

            final float coordX = coordinates.get() - particleRadius;
            final float coordY = coordinates.get() - particleRadius;

            particlesTrianglesCoordinates.put(coordX);
            particlesTrianglesCoordinates.put(coordY);
            particlesTrianglesCoordinates.put(coordX + particleSize);
            particlesTrianglesCoordinates.put(coordY);
            particlesTrianglesCoordinates.put(coordX + particleSize);
            particlesTrianglesCoordinates.put(coordY + particleSize);

            particlesTrianglesCoordinates.put(coordX);
            particlesTrianglesCoordinates.put(coordY);
            particlesTrianglesCoordinates.put(coordX);
            particlesTrianglesCoordinates.put(coordY + particleSize);
            particlesTrianglesCoordinates.put(coordX + particleSize);
            particlesTrianglesCoordinates.put(coordY + particleSize);
        }
    }

    private void drawParticles(
            @ColorInt final int color,
            final int count) {
        setColor(gl, color);

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        gl.glBindTexture(GL10.GL_TEXTURE_2D, texId);

        particlesTexturesCoordinates.position(0);
        particlesTrianglesCoordinates.position(0);

        gl.glTexCoordPointer(2, GL10.GL_BYTE, 0, particlesTexturesCoordinates);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, particlesTrianglesCoordinates);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, count * VERTICES_PER_PARTICLE);

        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
    }

    private void setColor(@NonNull final GL10 gl, @ColorInt final int color) {
        gl.glColor4f(
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                Color.alpha(color) / 255f);
    }
}
