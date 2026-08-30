/* SPDX-License-Identifier: MIT */

package io.github.janguenter.bluemap.arsnouveau.model;

import java.util.List;

/** Immutable static mesh compiled from an operator-installed Bedrock GEO file. */
public record InstalledGeoModel(List<Quad> quads) {

    public InstalledGeoModel {
        quads = List.copyOf(quads);
        if (quads.isEmpty()) {
            throw new IllegalArgumentException("installed GEO model is empty");
        }
    }

    public record Quad(Vertex first, Vertex second, Vertex third, Vertex fourth) {

        public Vec3 normal() {
            return second.position().subtract(first.position())
                    .cross(fourth.position().subtract(first.position()))
                    .normalizedOr(new Vec3(0D, 1D, 0D));
        }
    }

    public record Vertex(Vec3 position, float u, float v) {
    }

    public record Vec3(double x, double y, double z) {

        public Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        public Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        public Vec3 scale(double factor) {
            return new Vec3(x * factor, y * factor, z * factor);
        }

        public Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        public Vec3 normalizedOr(Vec3 fallback) {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length <= 1.0E-12D ? fallback : scale(1D / length);
        }

        public Vec3 rotateX(double degrees) {
            double angle = Math.toRadians(degrees);
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            return new Vec3(x, y * cosine - z * sine, y * sine + z * cosine);
        }

        public Vec3 rotateY(double degrees) {
            double angle = Math.toRadians(degrees);
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            return new Vec3(x * cosine + z * sine, y, -x * sine + z * cosine);
        }

        public Vec3 rotateZ(double degrees) {
            double angle = Math.toRadians(degrees);
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            return new Vec3(x * cosine - y * sine, x * sine + y * cosine, z);
        }

        /** Matches the point order used by the installed GeckoLib geometry. */
        public Vec3 rotate(Vec3 degrees) {
            return rotateX(degrees.x).rotateY(degrees.y).rotateZ(degrees.z);
        }

        public Vec3 rotateAbout(Vec3 pivot, Vec3 degrees) {
            return subtract(pivot).rotate(degrees).add(pivot);
        }
    }
}
