package traben.entity_model_features.model;

import java.awt.image.BufferedImage;

/** Reprojects legacy chest atlases using the ModelBox face contract, before any lid rotation. */
public final class ChestAtlas {

    private record Point(double x, double y, double z) {

        Point plus(Point other, double factor) {
            return new Point(x + other.x * factor, y + other.y * factor, z + other.z * factor);
        }

        Point minus(Point other) { return new Point(x - other.x, y - other.y, z - other.z); }

        double dot(Point other) { return x * other.x + y * other.y + z * other.z; }
    }

    private record Face(Point a, Point b, Point d, double u1, double v1, double u2, double v2) {

        Point at(double u, double v) {
            return a.plus(b.minus(a), (u2 - u) / (u2 - u1)).plus(d.minus(a), (v - v1) / (v2 - v1));
        }

        double[] uv(Point point) {
            Point ab = b.minus(a), ad = d.minus(a), ap = point.minus(a);
            double s = ap.dot(ab) / ab.dot(ab), t = ap.dot(ad) / ad.dot(ad);
            return new double[]{u2 + (u1 - u2) * s, v1 + (v2 - v1) * t};
        }
    }

    public static BufferedImage split(BufferedImage original, boolean left) {
        if (original.getWidth() % 128 != 0 || original.getHeight() != original.getWidth() / 2)
            throw new IllegalArgumentException("Double chest atlas must be a scaled 128x64 image");
        int scale = original.getWidth() / 128;
        BufferedImage result = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
        double x = left ? 0 : 1;
        copy(result, original, scale, left, faces(x, 9, 1, 15, 5, 14, 0, 0), faces(1, 2, 1, 30, 5, 14, 0, 0));
        copy(result, original, scale, left, faces(x, 0, 1, 15, 10, 14, 0, 19), faces(1, 6, 1, 30, 10, 14, 0, 19));
        copy(result, original, scale, left, faces(left ? 0 : 15, 7, 15, 1, 4, 1, 0, 0), faces(15, 5, 0, 2, 4, 1, 0, 0));
        return result;
    }

    public static BufferedImage single(BufferedImage original) {
        if (original.getWidth() % 64 != 0 || original.getHeight() != original.getWidth())
            throw new IllegalArgumentException("Single chest atlas must be a scaled 64x64 image");
        int scale = original.getWidth() / 64;
        BufferedImage result = new BufferedImage(64 * scale, 64 * scale, BufferedImage.TYPE_INT_ARGB);
        copy(result, original, scale, false, faces(1, 9, 1, 14, 5, 14, 0, 0), faces(1, 2, 1, 14, 5, 14, 0, 0));
        copy(result, original, scale, false, faces(1, 0, 1, 14, 10, 14, 0, 19), faces(1, 6, 1, 14, 10, 14, 0, 19));
        copy(result, original, scale, false, faces(7, 7, 15, 2, 4, 1, 0, 0), faces(7, 5, 0, 2, 4, 1, 0, 0));
        return result;
    }

    private static void copy(BufferedImage output, BufferedImage input, int scale, boolean left,
                             Face[] modern, Face[] legacy) {
        int[] oldFace = {0, 1, 3, 2, 5, 4};
        for (int index = 0; index < 6; index++) {
            Face face = modern[index], source = legacy[oldFace[index]];
            int minU = (int) Math.min(face.u1, face.u2) * scale;
            int maxU = (int) Math.max(face.u1, face.u2) * scale;
            int minV = (int) Math.min(face.v1, face.v2) * scale;
            int maxV = (int) Math.max(face.v1, face.v2) * scale;
            for (int v = minV; v < maxV; v++) for (int u = minU; u < maxU; u++) {
                Point point = face.at((u + .5) / scale, (v + .5) / scale);
                // C = T(0,16,16) * S(1,-1,-1); the left half is one block to the right.
                double[] uv = source.uv(new Point(point.x + (left ? 16 : 0), 16 - point.y, 16 - point.z));
                int sourceU = Math.max(0, Math.min(input.getWidth() - 1, (int) Math.floor(uv[0] * scale)));
                int sourceV = Math.max(0, Math.min(input.getHeight() - 1, (int) Math.floor(uv[1] * scale)));
                output.setRGB(u, v, input.getRGB(sourceU, sourceV));
            }
        }
    }

    private static Face[] faces(double x, double y, double z, double w, double h, double depth, int u, int v) {
        Point p000 = new Point(x, y, z), p100 = new Point(x + w, y, z);
        Point p110 = new Point(x + w, y + h, z), p010 = new Point(x, y + h, z);
        Point p001 = new Point(x, y, z + depth), p101 = new Point(x + w, y, z + depth);
        Point p111 = new Point(x + w, y + h, z + depth), p011 = new Point(x, y + h, z + depth);
        return new Face[]{
                new Face(p101, p100, p111, u + depth + w, v + depth, u + depth + w + depth, v + depth + h),
                new Face(p000, p001, p010, u, v + depth, u + depth, v + depth + h),
                new Face(p101, p001, p100, u + depth, v, u + depth + w, v + depth),
                new Face(p110, p010, p111, u + depth + w, v + depth, u + depth + w + w, v),
                new Face(p100, p000, p110, u + depth, v + depth, u + depth + w, v + depth + h),
                new Face(p001, p101, p011, u + depth + w + depth, v + depth, u + depth + w + depth + w, v + depth + h)
        };
    }
}
