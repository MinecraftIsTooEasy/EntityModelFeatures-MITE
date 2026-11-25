package traben.entity_model_features.animation;

public final class MotionMath {

    public static double direction(double x, double z, double yaw, boolean strafe) {
        double length = Math.hypot(x, z);
        if (length == 0) return 0;
        double angle = Math.toRadians(90 - yaw);
        return -(strafe ? x * Math.sin(angle) + z * Math.cos(angle)
                : x * Math.cos(angle) - z * Math.sin(angle)) / length;
    }

    public static double lerp(double previous, double current, double delta) {
        return previous + (current - previous) * delta;
    }

    public static double angle(double previous, double current, double delta) {
        double difference = (current - previous) % 360;
        if (difference < -180) difference += 360;
        if (difference >= 180) difference -= 360;
        return Math.toRadians(previous + difference * delta);
    }
}
