package traben.entity_model_features.animation;

public final class RenderMath {

    /** MITE draws two triangle strips, with two vertices at each of the 25 rope samples. */
    public static double leashOffset(int vertex, double start, double end, boolean vertical) {
        double t = (vertex % 50 / 2) / 24.0;
        double fraction = vertical ? (t * t + t) * .5 : t;
        return start + (end - start) * fraction;
    }

    public static double rotateX(double x, double z, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return x * Math.cos(yaw) - z * Math.sin(yaw);
    }

    public static double rotateZ(double x, double z, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return x * Math.sin(yaw) + z * Math.cos(yaw);
    }
}
