package com.lyrinth.advancedmarriage.model;

public class HomeLocation {
    private final long marriageId;
    private final String worldName;
    private final String serverId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomeLocation(long marriageId, String worldName, String serverId, double x, double y, double z, float yaw, float pitch) {
        this.marriageId = marriageId;
        this.worldName = worldName;
        this.serverId = serverId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public long getMarriageId() {
        return marriageId;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getServerId() {
        return serverId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}

