/* SPDX-License-Identifier: MIT */

package io.github.janguenter.bluemap.arscreo.model;

import io.github.janguenter.bluemap.arscreo.model.WheelModel.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable sampled GeckoLib bone transforms in compiled model coordinates. */
public record WheelPose(Map<String, BoneTransform> bones) {

    public static final WheelPose BASE = new WheelPose(Map.of());

    public WheelPose {
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
