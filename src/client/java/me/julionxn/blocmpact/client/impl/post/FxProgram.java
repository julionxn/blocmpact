package me.julionxn.blocmpact.client.impl.post;

import me.julionxn.blocmpact.Blocmpact;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A full-screen fragment program: {@code #version 150} + defines + {@code blocmpact:lib/core.glsl} + the
 * {@code .fsh}, with {@code #include}s expanded once each and {@code #line} directives so driver errors point at the
 * right file and line (they are translated back to file names in the report).
 */
public final class FxProgram {
    public static final Identifier CORE = Blocmpact.id("lib/core.glsl");
    static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]\\s*$");
    private static final Pattern ERROR_REF = Pattern.compile("(?<![\\w.])(\\d+)[:(](\\d+)\\)?");
    static final String VERTEX = """
        #version 150
        out vec2 texCoord;
        void main() {
            vec2 p[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
            gl_Position = vec4(p[gl_VertexID], 0.0, 1.0);
            texCoord = p[gl_VertexID] * 0.5 + 0.5;
        }
        """;

    final int id;
    private final Map<String, Integer> locations = new HashMap<>();

    private FxProgram(int id) {
        this.id = id;
    }

    int location(String uniform) {
        return locations.computeIfAbsent(uniform, k -> GL20.glGetUniformLocation(id, k));
    }

    void delete() {
        GL20.glDeleteProgram(id);
    }

    /** Assembled fragment source and the list of files in it (index 1 = first). */
    public record Source(String text, List<String> files) {
    }

    public static Source assemble(Identifier program, Map<String, String> defines) throws IOException {
        List<String> files = new ArrayList<>();
        Set<Identifier> included = new HashSet<>();
        StringBuilder src = new StringBuilder("#version 150\n");
        defines.forEach((k, v) -> src.append("#define ").append(k).append(' ').append(v).append('\n'));
        append(src, CORE, files, included);
        append(src, fsh(program), files, included);
        return new Source(src.toString(), files);
    }

    static FxProgram compile(Identifier program, Map<String, String> defines) throws IOException {
        Source source = assemble(program, defines);
        int vs = shader(GL20.GL_VERTEX_SHADER, VERTEX, List.of("<vertex>"));
        int fs;
        try {
            fs = shader(GL20.GL_FRAGMENT_SHADER, source.text(), source.files());
        } catch (IOException e) {
            GL20.glDeleteShader(vs);
            throw e;
        }
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL30.glBindFragDataLocation(p, 0, "fragColor");
        GL20.glLinkProgram(p);
        GL20.glDetachShader(p, vs);
        GL20.glDetachShader(p, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            throw new IOException("link " + program + ": " + translate(log, source.files()));
        }
        return new FxProgram(p);
    }

    private static int shader(int type, String src, List<String> files) throws IOException {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IOException(translate(log, files));
        }
        return s;
    }

    /** Rewrites "2:54" / "2(54)" references in a driver log as "file.fsh:54". */
    static String translate(String log, List<String> files) {
        Matcher m = ERROR_REF.matcher(log);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            String rep = index >= 1 && index <= files.size() ? files.get(index - 1) + ":" + m.group(2) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    /** {@code ns:path} → {@code ns:fx/path.fsh}. */
    public static Identifier fsh(Identifier program) {
        return Identifier.fromNamespaceAndPath(program.getNamespace(), "fx/" + program.getPath() + ".fsh");
    }

    /** {@code "ns:path.glsl"} or {@code "path.glsl"} (relative to the including file's namespace) → resource id. */
    static Identifier resolveInclude(String ref, Identifier from) {
        String r = ref.trim();
        if (r.contains(":")) {
            Identifier id = Identifier.parse(r);
            return Identifier.fromNamespaceAndPath(id.getNamespace(), "fx/" + id.getPath());
        }
        return Identifier.fromNamespaceAndPath(from.getNamespace(), "fx/" + r);
    }

    /** In GLSL 1.50 "#line n" numbers the NEXT line as n + 1, hence "#line 0 file" at the top of each file. */
    private static void append(StringBuilder out, Identifier file, List<String> files, Set<Identifier> included) throws IOException {
        Identifier resource = file.getPath().startsWith("fx/") ? file : Identifier.fromNamespaceAndPath(file.getNamespace(), "fx/" + file.getPath());
        if (!included.add(resource)) return;
        files.add(resource.toString());
        int index = files.size();
        String[] lines = read(resource).split("\\R", -1);
        out.append("#line 0 ").append(index).append('\n');
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INCLUDE.matcher(lines[i]);
            if (m.matches()) {
                append(out, resolveInclude(m.group(1), resource), files, included);
                out.append("#line ").append(i + 1).append(' ').append(index).append('\n');
            } else if (lines[i].trim().startsWith("#version")) {
                out.append("// ").append(lines[i]).append('\n'); // only one #version, added by Blocmpact
            } else {
                out.append(lines[i]).append('\n');
            }
        }
    }

    static String read(Identifier resource) throws IOException {
        Resource r = Minecraft.getInstance().getResourceManager().getResource(resource)
            .orElseThrow(() -> new IOException("missing " + resource));
        try (InputStream in = r.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
