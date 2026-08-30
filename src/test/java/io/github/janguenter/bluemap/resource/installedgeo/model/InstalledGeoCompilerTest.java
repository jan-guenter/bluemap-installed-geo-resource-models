/* SPDX-License-Identifier: MIT */

package io.github.janguenter.bluemap.resource.installedgeo.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoCompiler.Contract;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Quad;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Vec3;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoModel.Vertex;
import io.github.janguenter.bluemap.resource.installedgeo.model.InstalledGeoPose.BoneTransform;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InstalledGeoCompilerTest {

    private static final int MAX_BYTES = 64 * 1024;
    private static final String BASE_FINGERPRINT =
            "0230e47798bf74f9b94055f2ddaf757ae21bd3ce52d30f714c5078e684bf38b6";
    private static final String STATIC_FINGERPRINT =
            "4e654a1b2b7d9c910d5b21b69cc2b6dc26d94598481d042a6debd8348d69cf06";
    private static final String POSED_FINGERPRINT =
            "7a583c96dcc914027a0440ad8db1452c9a81b65fd4386f07e9df4384e5422b2c";
    private static final Contract BASE_CONTRACT = new Contract(20, 36, 192);
    private static final String DEFAULT_CUBE = cube(
            "[0,0,0]",
            "[16,16,16]",
            null,
            null,
            "[4,8]",
            "[8,16]",
            true
    );
    private static final String HIDDEN_CUBE = cube(
            "[0,0,0]",
            "[16,16,16]",
            null,
            null,
            "[4,8]",
            "[8,16]",
            false
    );

    @Test
    void locksCompleteBaseMeshAtRawBitPrecision() throws IOException {
        InstalledGeoModel implicit = InstalledGeoCompiler.compile(
                baseGeometry(), BASE_CONTRACT
        );
        InstalledGeoModel explicit = InstalledGeoCompiler.compile(
                baseGeometry(), BASE_CONTRACT, InstalledGeoPose.BASE
        );

        assertEquals(implicit, explicit);
        assertEquals(192, implicit.quads().size());
        assertEquals(BASE_FINGERPRINT, fingerprint(implicit));
        assertEquals(24_610, fingerprintPayload(implicit).length);
        assertEquals(
                new Vertex(new Vec3(-1D, 1D, 1D), 0.1875F, 0.25F),
                implicit.quads().getFirst().first()
        );
    }

    @Test
    void locksStaticHierarchyAndSampledPoseTransformOrder() throws IOException {
        byte[] raw = complexGeometry();
        InstalledGeoModel base = InstalledGeoCompiler.compile(raw, BASE_CONTRACT);
        InstalledGeoPose pose = new InstalledGeoPose(Map.of(
                "bone_1",
                new BoneTransform(
                        new Vec3(13D, -17D, 19D),
                        new Vec3(0.125D, -0.25D, 0.375D)
                ),
                "bone_0",
                new BoneTransform(
                        new Vec3(-29D, 31D, -37D),
                        new Vec3(-0.5D, 0.625D, -0.75D)
                )
        ));
        InstalledGeoModel posed = InstalledGeoCompiler.compile(
                raw, BASE_CONTRACT, pose
        );

        assertEquals(STATIC_FINGERPRINT, fingerprint(base));
        assertEquals(POSED_FINGERPRINT, fingerprint(posed));
        assertEquals(0xbfefb5fb31577971L, Double.doubleToRawLongBits(
                posed.quads().getFirst().first().position().x()
        ));
        assertEquals(0x3ff24fff18d0e619L, Double.doubleToRawLongBits(
                posed.quads().getFirst().first().position().y()
        ));
        assertEquals(0x3fe0b01aa4f28294L, Double.doubleToRawLongBits(
                posed.quads().getFirst().first().position().z()
        ));
    }

    @Test
    void rejectsUnknownPoseBoneBeforeLaterContractChecks() {
        InstalledGeoPose pose = new InstalledGeoPose(Map.of(
                "not_a_bone",
                new BoneTransform(new Vec3(1D, 2D, 3D), new Vec3(4D, 5D, 6D))
        ));

        assertRejected(
                "installed run references an unknown GEO bone",
                () -> InstalledGeoCompiler.compile(
                        baseGeometry(), new Contract(20, 35, 192), pose
                )
        );
    }

    @Test
    void enforcesRawByteBoundsExactly() {
        byte[] valid = baseGeometry();
        byte[] atLimit = Arrays.copyOf(valid, MAX_BYTES);
        Arrays.fill(atLimit, valid.length, atLimit.length, (byte) ' ');

        assertEquals(
                192,
                InstalledGeoCompiler.compile(atLimit, BASE_CONTRACT).quads().size()
        );
        assertRejected(
                "installed GEO is outside the byte budget",
                () -> InstalledGeoCompiler.compile(new byte[1], BASE_CONTRACT)
        );
        assertRejected(
                "root must be an object",
                () -> InstalledGeoCompiler.compile(bytes("[]"), BASE_CONTRACT)
        );
        assertRejected(
                "installed GEO is outside the byte budget",
                () -> InstalledGeoCompiler.compile(
                        new byte[MAX_BYTES + 1], BASE_CONTRACT
                )
        );
    }

    @Test
    void enforcesBoneCountAndDepthBoundsExactly() {
        byte[] one = geometry(
                "64", "32",
                List.of(bone("bone_0", null, null, null, List.of(DEFAULT_CUBE)))
        );
        List<String> sixtyFour = bones(64, 0, 1, false);
        List<String> sixtyFive = bones(65, 0, 1, false);
        List<String> depthThirtyTwo = bones(32, 31, 1, true);
        List<String> depthThirtyThree = bones(33, 32, 1, true);

        assertEquals(6, compile(one, new Contract(1, 1, 6)).quads().size());
        assertEquals(
                6,
                compile(
                        geometry("64", "32", sixtyFour),
                        new Contract(64, 1, 6)
                ).quads().size()
        );
        assertRejected(
                "installed GEO bone count is outside budget",
                () -> compile(geometry("64", "32", List.of()), new Contract(1, 1, 6))
        );
        assertRejected(
                "installed GEO bone count is outside budget",
                () -> compile(
                        geometry("64", "32", sixtyFive),
                        new Contract(65, 1, 6)
                )
        );
        assertEquals(
                6,
                compile(
                        geometry("64", "32", depthThirtyTwo),
                        new Contract(32, 1, 6)
                ).quads().size()
        );
        assertRejected(
                "cyclic or deep installed GEO hierarchy",
                () -> compile(
                        geometry("64", "32", depthThirtyThree),
                        new Contract(33, 1, 6)
                )
        );
    }

    @Test
    void enforcesCubeCountBoundExactly() {
        byte[] atLimit = geometry(
                "64", "32", bones(1, 0, 256, false)
        );
        byte[] overLimit = geometry(
                "64", "32", bones(1, 0, 257, false)
        );

        assertEquals(
                6,
                compile(atLimit, new Contract(1, 256, 6)).quads().size()
        );
        assertRejected(
                "installed GEO cube budget exceeded",
                () -> compile(overLimit, new Contract(1, 257, 6))
        );
    }

    @Test
    void enforcesStringBoundsExactly() {
        String name256 = "n".repeat(256);
        String name257 = "n".repeat(257);

        assertEquals(
                6,
                compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        name256, null, null, null,
                                        List.of(DEFAULT_CUBE)
                                ))
                        ),
                        new Contract(1, 1, 6)
                ).quads().size()
        );
        assertRejected(
                "installed GEO string is outside budget",
                () -> compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        name257, null, null, null,
                                        List.of(DEFAULT_CUBE)
                                ))
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "installed GEO string is outside budget",
                () -> compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        " ", null, null, null,
                                        List.of(DEFAULT_CUBE)
                                ))
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "malformed installed GEO string",
                () -> compile(
                        simpleGeometry().replace(
                                "\"name\":\"bone_0\"",
                                "\"name\":0"
                        ),
                        new Contract(1, 1, 6)
                )
        );
    }

    @Test
    void enforcesTextureDimensionBoundsExactly() {
        assertEquals(
                6,
                compile(
                        geometry(
                                "1", "4096",
                                List.of(bone(
                                        "bone_0", null, null, null,
                                        List.of(DEFAULT_CUBE)
                                ))
                        ),
                        new Contract(1, 1, 6)
                ).quads().size()
        );
        for (String invalid : List.of("0", "4097", "1.5")) {
            assertRejected(
                    "malformed installed GEO dimension",
                    () -> compile(
                            geometry(
                                    invalid, "32",
                                    List.of(bone(
                                            "bone_0", null, null, null,
                                            List.of(DEFAULT_CUBE)
                                    ))
                            ),
                            new Contract(1, 1, 6)
                    )
            );
        }
    }

    @Test
    void enforcesGeneralNumberBoundsExactly() {
        String boundaryCube = cube(
                "[16384,-16384,16384]",
                "[1,1,1]",
                null,
                null,
                "[0,0]",
                "[1,1]",
                true
        );
        assertEquals(
                6,
                compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        "bone_0", null, null, null,
                                        List.of(boundaryCube)
                                ))
                        ),
                        new Contract(1, 1, 6)
                ).quads().size()
        );
        assertRejected(
                "installed GEO number is out of bounds",
                () -> compile(
                        simpleGeometry().replace(
                                "\"origin\":[0,0,0]",
                                "\"origin\":[16385,0,0]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "installed GEO number is out of bounds",
                () -> compile(
                        simpleGeometry().replace(
                                "\"origin\":[0,0,0]",
                                "\"origin\":[1e9999,0,0]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "malformed installed GEO number",
                () -> compile(
                        simpleGeometry().replace(
                                "\"origin\":[0,0,0]",
                                "\"origin\":[\"zero\",0,0]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
    }

    @Test
    void rejectsMalformedTopLevelSchemaWithExactMessages() {
        assertThrows(
                JsonParseException.class,
                () -> InstalledGeoCompiler.compile(bytes("{]"), BASE_CONTRACT)
        );
        assertRejected(
                "unsupported installed GEO version",
                () -> compile(
                        simpleGeometry().replace("\"1.12.0\"", "\"9.99.0\""),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "installed GEO geometry count changed",
                () -> compile(
                        "{\"format_version\":\"1.12.0\","
                                + "\"minecraft:geometry\":[]}",
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "geometry must be an array",
                () -> compile(
                        "{\"format_version\":\"1.12.0\","
                                + "\"minecraft:geometry\":{}}",
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "description must be an object",
                () -> compile(
                        "{\"format_version\":\"1.12.0\","
                                + "\"minecraft:geometry\":[{"
                                + "\"description\":[],\"bones\":[]}]}",
                        new Contract(1, 1, 6)
                )
        );
    }

    @Test
    void rejectsMalformedCubeSchemaWithExactMessages() {
        assertRejected(
                "cube origin must contain three numbers",
                () -> compile(
                        simpleGeometry().replace(
                                "\"origin\":[0,0,0]",
                                "\"origin\":[0,0]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "negative installed GEO cube size",
                () -> compile(
                        simpleGeometry().replace(
                                "\"size\":[16,16,16]",
                                "\"size\":[-1,16,16]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "installed GEO cube schema changed",
                () -> compile(
                        simpleGeometry().replace(
                                "\"size\":[16,16,16]",
                                "\"size\":[16,16,16],\"inflate\":1"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "mapped cube UV must be an object",
                () -> compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        "bone_0", null, null, null,
                                        List.of(
                                                "{\"origin\":[0,0,0],"
                                                        + "\"size\":[16,16,16],"
                                                        + "\"uv\":[]}"
                                        )
                                ))
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "cubes must be an array",
                () -> compile(
                        geometry(
                                "64", "32",
                                List.of("{\"name\":\"bone_0\",\"cubes\":{}}")
                        ),
                        new Contract(1, 1, 6)
                )
        );
    }

    @Test
    void rejectsMalformedFaceUvWithExactMessage() {
        assertRejected(
                "malformed installed GEO face UV",
                () -> compile(
                        simpleGeometry().replace(
                                "\"uv\":[4,8]",
                                "\"uv\":[4]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "malformed installed GEO face UV",
                () -> compile(
                        simpleGeometry().replace(
                                "\"uv_size\":[8,16]",
                                "\"uv_size\":[8,16,24]"
                        ),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "face UV must be an object",
                () -> compile(
                        geometry(
                                "64", "32",
                                List.of(bone(
                                        "bone_0", null, null, null,
                                        List.of(
                                                "{\"origin\":[0,0,0],"
                                                        + "\"size\":[16,16,16],"
                                                        + "\"uv\":{\"west\":[]}}"
                                        )
                                ))
                        ),
                        new Contract(1, 1, 6)
                )
        );
    }

    @Test
    void rejectsDuplicateMissingAndCyclicHierarchy() {
        List<String> duplicate = List.of(
                bone("same", null, null, null, List.of(DEFAULT_CUBE)),
                bone("same", null, null, null, List.of())
        );
        List<String> missing = List.of(
                bone("bone_0", "absent", null, null, List.of(DEFAULT_CUBE))
        );
        List<String> cycle = List.of(
                bone("bone_0", "bone_1", null, null, List.of(DEFAULT_CUBE)),
                bone("bone_1", "bone_0", null, null, List.of())
        );

        assertRejected(
                "duplicate installed GEO bone",
                () -> compile(
                        geometry("64", "32", duplicate),
                        new Contract(2, 1, 6)
                )
        );
        assertRejected(
                "missing installed GEO parent bone",
                () -> compile(
                        geometry("64", "32", missing),
                        new Contract(1, 1, 6)
                )
        );
        assertRejected(
                "cyclic or deep installed GEO hierarchy",
                () -> compile(
                        geometry("64", "32", cycle),
                        new Contract(2, 1, 6)
                )
        );
    }

    @Test
    void preservesAllRosterFallbackSignals() {
        String raw = simpleGeometry();

        assertRejected(
                "installed GEO bone roster changed",
                () -> compile(raw, new Contract(2, 1, 6))
        );
        assertRejected(
                "installed GEO cube roster changed",
                () -> compile(raw, new Contract(1, 2, 6))
        );
        assertRejected(
                "installed GEO face roster changed",
                () -> compile(raw, new Contract(1, 1, 5))
        );
    }

    @Test
    void preservesZeroSizeFaceBehavior() {
        String flat = cube(
                "[0,0,0]",
                "[0,16,16]",
                null,
                null,
                "[4,8]",
                "[8,16]",
                true
        );
        byte[] raw = geometry(
                "64", "32",
                List.of(bone("bone_0", null, null, null, List.of(flat)))
        );

        assertEquals(
                2,
                compile(raw, new Contract(1, 1, 2)).quads().size()
        );
    }

    @Test
    void validatesContractModelAndPoseRecords() {
        assertRejected(
                "installed GEO contract is empty",
                () -> new Contract(0, 1, 1)
        );
        assertRejected(
                "installed GEO contract is empty",
                () -> new Contract(1, 0, 1)
        );
        assertRejected(
                "installed GEO contract is empty",
                () -> new Contract(1, 1, 0)
        );

        assertRejected(
                "installed GEO model is empty",
                () -> new InstalledGeoModel(List.of())
        );
        assertRejected(
                "wheel bone transform is incomplete",
                () -> new BoneTransform(null, new Vec3(0D, 0D, 0D))
        );

        Map<String, BoneTransform> mutable = new LinkedHashMap<>();
        mutable.put("bone_0", BoneTransform.IDENTITY);
        InstalledGeoPose pose = new InstalledGeoPose(mutable);
        mutable.clear();
        assertEquals(BoneTransform.IDENTITY, pose.transform("bone_0"));
        assertEquals(BoneTransform.IDENTITY, pose.transform("missing"));
    }

    @Test
    void modelCopiesInputAndComputesNormals() {
        Quad quad = new Quad(
                new Vertex(new Vec3(0D, 0D, 0D), 0F, 0F),
                new Vertex(new Vec3(1D, 0D, 0D), 1F, 0F),
                new Vertex(new Vec3(1D, 0D, 1D), 1F, 1F),
                new Vertex(new Vec3(0D, 0D, 1D), 0F, 1F)
        );
        List<Quad> mutable = new ArrayList<>();
        mutable.add(quad);
        InstalledGeoModel model = new InstalledGeoModel(mutable);
        mutable.clear();

        assertEquals(1, model.quads().size());
        assertEquals(new Vec3(0D, -1D, 0D), model.quads().getFirst().normal());
        assertNotSame(mutable, model.quads());
        assertTrue(model.quads().stream().allMatch(value -> value != null));
    }

    private static InstalledGeoModel compile(byte[] raw, Contract contract) {
        return InstalledGeoCompiler.compile(raw, contract);
    }

    private static InstalledGeoModel compile(String raw, Contract contract) {
        return compile(bytes(raw), contract);
    }

    private static void assertRejected(String message, Runnable compilation) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                compilation::run
        );
        assertEquals(message, exception.getMessage());
    }

    private static String fingerprint(InstalledGeoModel model) throws IOException {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            fingerprintPayload(model)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] fingerprintPayload(InstalledGeoModel model)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write("bluemap-installed-geo-mesh-v1".getBytes(
                    StandardCharsets.US_ASCII
            ));
            output.writeByte(0);
            output.writeInt(model.quads().size());
            for (Quad quad : model.quads()) {
                write(output, quad.first());
                write(output, quad.second());
                write(output, quad.third());
                write(output, quad.fourth());
            }
        }
        return bytes.toByteArray();
    }

    private static void write(DataOutputStream output, Vertex vertex)
            throws IOException {
        output.writeLong(Double.doubleToRawLongBits(vertex.position().x()));
        output.writeLong(Double.doubleToRawLongBits(vertex.position().y()));
        output.writeLong(Double.doubleToRawLongBits(vertex.position().z()));
        output.writeInt(Float.floatToRawIntBits(vertex.u()));
        output.writeInt(Float.floatToRawIntBits(vertex.v()));
    }

    private static byte[] baseGeometry() {
        List<String> cubes = cubes(36, DEFAULT_CUBE);
        for (int index = 32; index < cubes.size(); index++) {
            cubes.set(index, HIDDEN_CUBE);
        }
        List<String> bones = new ArrayList<>();
        bones.add(bone("bone_0", null, null, null, cubes));
        for (int bone = 1; bone < 20; bone++) {
            bones.add(bone("bone_" + bone, null, null, null, List.of()));
        }
        return geometry("64", "32", bones);
    }

    private static byte[] complexGeometry() {
        List<String> cubes = cubes(
                36,
                cube(
                        "[1,-2,3]",
                        "[14,12,10]",
                        "[-5,6,7]",
                        "[29,-31,37]",
                        "[3,5]",
                        "[7,11]",
                        true
                )
        );
        for (int index = 32; index < cubes.size(); index++) {
            cubes.set(index, HIDDEN_CUBE);
        }
        List<String> bones = new ArrayList<>();
        bones.add(bone(
                "bone_0", null, "[4,8,-2]", "[11,13,17]", List.of()
        ));
        bones.add(bone(
                "bone_1", "bone_0", "[-3,7,5]", "[-7,19,-23]", cubes
        ));
        for (int bone = 2; bone < 20; bone++) {
            bones.add(bone("bone_" + bone, null, null, null, List.of()));
        }
        return geometry("64", "32", bones);
    }

    private static String simpleGeometry() {
        return new String(
                geometry(
                        "64", "32",
                        List.of(bone(
                                "bone_0", null, null, null,
                                List.of(DEFAULT_CUBE)
                        ))
                ),
                StandardCharsets.UTF_8
        );
    }

    private static List<String> bones(
            int count,
            int cubeBone,
            int cubeCount,
            boolean chain
    ) {
        List<String> result = new ArrayList<>();
        for (int bone = 0; bone < count; bone++) {
            String parent = chain && bone > 0 ? "bone_" + (bone - 1) : null;
            List<String> cubes = bone == cubeBone
                    ? cubesWithOneVisible(cubeCount) : List.of();
            result.add(bone(
                    "bone_" + bone, parent, null, null, cubes
            ));
        }
        return result;
    }

    private static List<String> cubesWithOneVisible(int count) {
        List<String> result = cubes(count, HIDDEN_CUBE);
        if (!result.isEmpty()) {
            result.set(0, DEFAULT_CUBE);
        }
        return result;
    }

    private static List<String> cubes(int count, String cube) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(cube);
        }
        return result;
    }

    private static String bone(
            String name,
            String parent,
            String pivot,
            String rotation,
            List<String> cubes
    ) {
        StringBuilder result = new StringBuilder();
        result.append("{\"name\":\"").append(name).append('"');
        if (parent != null) {
            result.append(",\"parent\":\"").append(parent).append('"');
        }
        if (pivot != null) {
            result.append(",\"pivot\":").append(pivot);
        }
        if (rotation != null) {
            result.append(",\"rotation\":").append(rotation);
        }
        if (!cubes.isEmpty()) {
            result.append(",\"cubes\":[")
                    .append(String.join(",", cubes))
                    .append(']');
        }
        return result.append('}').toString();
    }

    private static String cube(
            String origin,
            String size,
            String pivot,
            String rotation,
            String uvOrigin,
            String uvSize,
            boolean visible
    ) {
        StringBuilder result = new StringBuilder();
        result.append("{\"origin\":").append(origin)
                .append(",\"size\":").append(size);
        if (pivot != null) {
            result.append(",\"pivot\":").append(pivot);
        }
        if (rotation != null) {
            result.append(",\"rotation\":").append(rotation);
        }
        result.append(",\"uv\":");
        if (!visible) {
            return result.append("{}}").toString();
        }
        result.append('{');
        String[] faces = {"west", "east", "north", "south", "up", "down"};
        for (int index = 0; index < faces.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append('"').append(faces[index]).append("\":{\"uv\":")
                    .append(uvOrigin)
                    .append(",\"uv_size\":").append(uvSize)
                    .append('}');
        }
        return result.append("}}").toString();
    }

    private static byte[] geometry(
            String textureWidth,
            String textureHeight,
            List<String> bones
    ) {
        return bytes("{\"format_version\":\"1.12.0\","
                + "\"minecraft:geometry\":[{\"description\":{"
                + "\"texture_width\":" + textureWidth + ","
                + "\"texture_height\":" + textureHeight + "},"
                + "\"bones\":[" + String.join(",", bones) + "]}]}");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
