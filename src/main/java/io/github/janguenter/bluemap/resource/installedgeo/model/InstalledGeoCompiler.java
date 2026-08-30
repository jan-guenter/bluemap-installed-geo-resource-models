/*
 * SPDX-License-Identifier: MIT
 *
 * Independently authored geometry interpreter for operator-installed Bedrock
 * GEO resources. No mod resource is packaged here.
 */

package io.github.janguenter.bluemap.resource.installedgeo.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Quad;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Vec3;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Vertex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles admitted operator-installed Bedrock GEO resources into meshes. */
public final class InstalledGeoCompiler {

    private static final int MAX_BYTES = 64 * 1024;
    private static final int MAX_BONES = 64;
    private static final int MAX_CUBES = 256;
    private static final int MAX_BONE_DEPTH = 32;
    private static final Vec3 ZERO = new Vec3(0D, 0D, 0D);

    private InstalledGeoCompiler() {
    }

    public static InstalledGeoModel compile(byte[] raw, Contract contract) {
        return compile(raw, contract, InstalledGeoPose.BASE);
    }

    public static InstalledGeoModel compile(
            byte[] raw,
            Contract contract,
            InstalledGeoPose pose
    ) {
        if (raw.length < 2 || raw.length > MAX_BYTES) {
            throw new IllegalArgumentException("installed GEO is outside the byte budget");
        }
        JsonObject root = object(JsonParser.parseString(
                new String(raw, StandardCharsets.UTF_8)
        ), "root");
        if (!"1.12.0".equals(string(root.get("format_version")))) {
            throw new IllegalArgumentException("unsupported installed GEO version");
        }
        JsonArray geometries = array(root.get("minecraft:geometry"), "geometry");
        if (geometries.size() != 1) {
            throw new IllegalArgumentException("installed GEO geometry count changed");
        }
        JsonObject geometry = object(geometries.get(0), "geometry");
        JsonObject description = object(geometry.get("description"), "description");
        int textureWidth = positiveInt(description.get("texture_width"));
        int textureHeight = positiveInt(description.get("texture_height"));
        Map<String, RawBone> bones = parseBones(array(geometry.get("bones"), "bones"));
        if (bones.size() != contract.bones()) {
            throw new IllegalArgumentException("installed GEO bone roster changed");
        }
        if (!bones.keySet().containsAll(pose.bones().keySet())) {
            throw new IllegalArgumentException("installed run references an unknown GEO bone");
        }
        int cubes = bones.values().stream().mapToInt(bone -> bone.cubes.size()).sum();
        if (cubes != contract.cubes()) {
            throw new IllegalArgumentException("installed GEO cube roster changed");
        }

        List<Quad> quads = new ArrayList<>();
        for (RawBone bone : bones.values()) {
            List<RawBone> chain = boneChain(bone, bones);
            for (RawCube cube : bone.cubes) {
                emitCube(cube, chain, textureWidth, textureHeight, pose, quads);
            }
        }
        if (quads.size() != contract.quads()) {
            throw new IllegalArgumentException("installed GEO face roster changed");
        }
        return new InstalledGeoModel(quads);
    }

    private static Map<String, RawBone> parseBones(JsonArray source) {
        if (source.isEmpty() || source.size() > MAX_BONES) {
            throw new IllegalArgumentException("installed GEO bone count is outside budget");
        }
        Map<String, RawBone> bones = new LinkedHashMap<>();
        int cubes = 0;
        for (JsonElement element : source) {
            JsonObject object = object(element, "bone");
            String name = string(object.get("name"));
            String parent = object.has("parent") ? string(object.get("parent")) : null;
            Vec3 pivot = object.has("pivot") ? signedPivot(object.get("pivot")) : ZERO;
            Vec3 rotation = object.has("rotation")
                    ? signedRotation(object.get("rotation")) : ZERO;
            List<RawCube> children = new ArrayList<>();
            if (object.has("cubes")) {
                for (JsonElement cube : array(object.get("cubes"), "cubes")) {
                    if (++cubes > MAX_CUBES) {
                        throw new IllegalArgumentException("installed GEO cube budget exceeded");
                    }
                    children.add(parseCube(object(cube, "cube")));
                }
            }
            RawBone previous = bones.put(name, new RawBone(
                    name, parent, pivot, rotation, List.copyOf(children)
            ));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate installed GEO bone");
            }
        }
        return bones;
    }

    private static RawCube parseCube(JsonObject object) {
        Vec3 rawOrigin = vector(object.get("origin"), "cube origin");
        Vec3 size = vector(object.get("size"), "cube size");
        if (size.x() < 0D || size.y() < 0D || size.z() < 0D) {
            throw new IllegalArgumentException("negative installed GEO cube size");
        }
        if (object.has("inflate") || object.has("mirror")) {
            throw new IllegalArgumentException("installed GEO cube schema changed");
        }
        Vec3 origin = new Vec3(
                -(rawOrigin.x() + size.x()) / 16D,
                rawOrigin.y() / 16D,
                rawOrigin.z() / 16D
        );
        Vec3 rotation = object.has("rotation")
                ? signedRotation(object.get("rotation")) : ZERO;
        Vec3 pivot = object.has("pivot") ? signedPivot(object.get("pivot")) : ZERO;
        JsonObject uv = object(object.get("uv"), "mapped cube UV");
        return new RawCube(origin, size, rotation, pivot, uv.deepCopy());
    }

    private static List<RawBone> boneChain(RawBone bone, Map<String, RawBone> bones) {
        List<RawBone> chain = new ArrayList<>();
        RawBone current = bone;
        while (current != null) {
            if (chain.size() >= MAX_BONE_DEPTH || chain.contains(current)) {
                throw new IllegalArgumentException("cyclic or deep installed GEO hierarchy");
            }
            chain.add(current);
            if (current.parent == null) {
                current = null;
            } else {
                current = bones.get(current.parent);
                if (current == null) {
                    throw new IllegalArgumentException("missing installed GEO parent bone");
                }
            }
        }
        return chain;
    }

    private static void emitCube(
            RawCube cube,
            List<RawBone> chain,
            int textureWidth,
            int textureHeight,
            InstalledGeoPose pose,
            List<Quad> output
    ) {
        VertexSet vertices = new VertexSet(cube.origin, cube.size.scale(1D / 16D));
        for (Face face : Face.values()) {
            if (zeroSizeFace(cube.size, face)) {
                continue;
            }
            UvRect uv = mappedUv(cube.uv, face);
            if (uv == null) {
                continue;
            }
            Vec3[] faceVertices = vertices.forFace(face);
            float u = (float) ((uv.u + uv.width) / textureWidth);
            float uWidth = (float) (uv.u / textureWidth);
            float v = (float) (uv.v / textureHeight);
            float vHeight = (float) ((uv.v + uv.height) / textureHeight);
            float[][] coordinates = {
                    {u, v}, {uWidth, v}, {uWidth, vHeight}, {u, vHeight}
            };
            Vertex[] transformed = new Vertex[4];
            for (int index = 0; index < transformed.length; index++) {
                Vec3 point = faceVertices[index].rotateAbout(cube.pivot, cube.rotation);
                for (RawBone transform : chain) {
                    InstalledGeoPose.BoneTransform animation =
                            pose.transform(transform.name);
                    point = point.rotateAbout(
                            transform.pivot,
                            transform.rotation.add(animation.rotation())
                    ).add(animation.translation());
                }
                transformed[index] = new Vertex(
                        point, coordinates[index][0], coordinates[index][1]
                );
            }
            output.add(new Quad(
                    transformed[0], transformed[1], transformed[2], transformed[3]
            ));
        }
    }

    private static boolean zeroSizeFace(Vec3 size, Face face) {
        if (size.x() == 0D) {
            return face.axis != Axis.X;
        }
        if (size.y() == 0D) {
            return face.axis != Axis.Y;
        }
        if (size.z() == 0D) {
            return face.axis != Axis.Z;
        }
        return false;
    }

    private static UvRect mappedUv(JsonObject mapping, Face face) {
        JsonElement element = mapping.get(face.wireName);
        if (element == null) {
            return null;
        }
        JsonObject details = object(element, "face UV");
        JsonArray uv = array(details.get("uv"), "face UV origin");
        JsonArray size = array(details.get("uv_size"), "face UV size");
        if (uv.size() != 2 || size.size() != 2) {
            throw new IllegalArgumentException("malformed installed GEO face UV");
        }
        return new UvRect(
                number(uv.get(0)), number(uv.get(1)),
                number(size.get(0)), number(size.get(1))
        );
    }

    private static Vec3 signedPivot(JsonElement value) {
        Vec3 raw = vector(value, "pivot");
        return new Vec3(-raw.x() / 16D, raw.y() / 16D, raw.z() / 16D);
    }

    private static Vec3 signedRotation(JsonElement value) {
        Vec3 raw = vector(value, "rotation");
        return new Vec3(-raw.x(), -raw.y(), raw.z());
    }

    private static Vec3 vector(JsonElement value, String label) {
        JsonArray array = array(value, label);
        if (array.size() != 3) {
            throw new IllegalArgumentException(label + " must contain three numbers");
        }
        return new Vec3(number(array.get(0)), number(array.get(1)), number(array.get(2)));
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String label) {
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(label + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("malformed installed GEO string");
        }
        String result = value.getAsString();
        if (result.isBlank() || result.length() > 256) {
            throw new IllegalArgumentException("installed GEO string is outside budget");
        }
        return result;
    }

    private static int positiveInt(JsonElement value) {
        double number = number(value);
        if (number != Math.rint(number) || number <= 0D || number > 4_096D) {
            throw new IllegalArgumentException("malformed installed GEO dimension");
        }
        return (int) number;
    }

    private static double number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("malformed installed GEO number");
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || Math.abs(result) > 16_384D) {
            throw new IllegalArgumentException("installed GEO number is out of bounds");
        }
        return result;
    }

    public record Contract(int bones, int cubes, int quads) {

        public Contract {
            if (bones <= 0 || cubes <= 0 || quads <= 0) {
                throw new IllegalArgumentException("installed GEO contract is empty");
            }
        }
    }

    private enum Axis {
        X, Y, Z
    }

    private enum Face {
        WEST("west", Axis.X),
        EAST("east", Axis.X),
        NORTH("north", Axis.Z),
        SOUTH("south", Axis.Z),
        UP("up", Axis.Y),
        DOWN("down", Axis.Y);

        private final String wireName;
        private final Axis axis;

        Face(String wireName, Axis axis) {
            this.wireName = wireName;
            this.axis = axis;
        }
    }

    private record RawBone(
            String name,
            String parent,
            Vec3 pivot,
            Vec3 rotation,
            List<RawCube> cubes
    ) {
    }

    private record RawCube(
            Vec3 origin,
            Vec3 size,
            Vec3 rotation,
            Vec3 pivot,
            JsonObject uv
    ) {
    }

    private record UvRect(double u, double v, double width, double height) {
    }

    private record VertexSet(
            Vec3 bottomLeftBack,
            Vec3 bottomRightBack,
            Vec3 topLeftBack,
            Vec3 topRightBack,
            Vec3 topLeftFront,
            Vec3 topRightFront,
            Vec3 bottomLeftFront,
            Vec3 bottomRightFront
    ) {

        VertexSet(Vec3 origin, Vec3 size) {
            this(
                    origin,
                    new Vec3(origin.x(), origin.y(), origin.z() + size.z()),
                    new Vec3(origin.x(), origin.y() + size.y(), origin.z()),
                    new Vec3(origin.x(), origin.y() + size.y(), origin.z() + size.z()),
                    new Vec3(origin.x() + size.x(), origin.y() + size.y(), origin.z()),
                    new Vec3(
                            origin.x() + size.x(), origin.y() + size.y(),
                            origin.z() + size.z()
                    ),
                    new Vec3(origin.x() + size.x(), origin.y(), origin.z()),
                    new Vec3(origin.x() + size.x(), origin.y(), origin.z() + size.z())
            );
        }

        Vec3[] forFace(Face face) {
            return switch (face) {
                case WEST -> new Vec3[]{
                    topRightBack, topLeftBack, bottomLeftBack, bottomRightBack
                };
                case EAST -> new Vec3[]{
                    topLeftFront, topRightFront, bottomRightFront, bottomLeftFront
                };
                case NORTH -> new Vec3[]{
                    topLeftBack, topLeftFront, bottomLeftFront, bottomLeftBack
                };
                case SOUTH -> new Vec3[]{
                    topRightFront, topRightBack, bottomRightBack, bottomRightFront
                };
                case UP -> new Vec3[]{
                    topRightBack, topRightFront, topLeftFront, topLeftBack
                };
                case DOWN -> new Vec3[]{
                    bottomLeftBack, bottomLeftFront, bottomRightFront, bottomRightBack
                };
            };
        }
    }
}
