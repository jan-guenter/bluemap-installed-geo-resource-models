/* SPDX-License-Identifier: MIT */

package io.github.janguenter.bluemap.resource.installedgeo.model;

import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable sampled bone transforms in compiled model coordinates. */
public record InstalledGeoPose(Map<String, BoneTransform> bones) {

    public static final InstalledGeoPose BASE = new InstalledGeoPose(Map.of());

    public InstalledGeoPose {
        bones = Map.copyOf(new LinkedHashMap<>(bones));
    }

    public BoneTransform transform(String bone) {
        return bones.getOrDefault(bone, BoneTransform.IDENTITY);
    }

    /** Rotation is in degrees; translation is in blocks. */
    public record BoneTransform(Vec3 rotation, Vec3 translation) {

        public static final BoneTransform IDENTITY = new BoneTransform(
                new Vec3(0D, 0D, 0D), new Vec3(0D, 0D, 0D)
        );

        public BoneTransform {
            if (rotation == null || translation == null) {
                throw new IllegalArgumentException("wheel bone transform is incomplete");
            }
        }
    }
}
