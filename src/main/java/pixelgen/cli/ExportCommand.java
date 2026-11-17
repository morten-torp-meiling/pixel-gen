package pixelgen.cli;

import pixelgen.Cli;
import pixelgen.godot.ExportConfig;
import pixelgen.godot.ExportState;
import pixelgen.godot.Exporter;
import pixelgen.parse.PaletteResolver;
import pixelgen.parse.ParseException;
import pixelgen.parse.ProjectFiles;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code export [--all]} writes every sprite and tileset under {@code assets/} into the Godot
 * project named in {@code pixelgen.conf}.
 * <ul>
 *   <li>Only sources that changed are exported: each source's hash (its text, palette colours and
 *       the settings) is kept in {@link ExportState#FILE}. A source whose files are missing from
 *       the Godot project is exported again too. {@code --all} exports everything.</li>
 *   <li>Files the export wrote earlier but no longer writes (a deleted sprite or variant) are
 *       removed, together with Godot's {@code .import} file for them. Nothing else is touched.</li>
 *   <li>A source with errors is reported and left as it was last exported.</li>
 * </ul>
 */
public final class ExportCommand implements Cli.Command {

    private final Path projectDir;

    public ExportCommand(Path projectDir) { this.projectDir = projectDir; }

    public String usage() { return "export [--all]"; }

    public String summary() { return "Export changed assets/ files into the Godot project set in pixelgen.conf"; }

    public int run(List<String> args, PrintStream out, PrintStream err) throws Exception {
        Options options = Options.parse(args, Set.of("--all"), Set.of());
        if (!options.words().isEmpty()) throw new Cli.UsageException("expected: " + usage());
        boolean all = options.has("--all");
        ExportConfig config;
        try {
            config = ExportConfig.load(projectDir);
        } catch (NoSuchFileException e) {
            err.println(e.getReason());
            return 1;
        } catch (ParseException e) {
            for (String error : e.errors()) err.println(error);
            return 1;
        }
        Path assets = projectDir.resolve("assets");
        if (!Files.isDirectory(assets)) {
            err.println("no assets/ folder in " + projectDir);
            return 1;
        }
        List<Path> files = SpriteFiles.collect(projectDir, List.of("assets"), SpriteFiles.ALL);

        Path statePath = projectDir.resolve(ExportState.FILE);
        ExportState old = ExportState.load(statePath);
        if (old != null && !old.project().equals(config.project())) {
            out.println("note: the Godot project changed from " + old.project() + "; exporting everything,"
                    + " and leaving the files in the old project alone");
            old = null;
        }
        if (old == null) old = ExportState.empty(config.project());

        PaletteResolver palettes = PaletteResolver.cached(projectDir);
        Map<String, ExportState.Entry> sources = new TreeMap<>();
        Map<String, String> writtenBy = new HashMap<>();
        int exported = 0, unchanged = 0, failed = 0;
        for (Path f : files) {
            String path = ProjectFiles.display(projectDir, f);
            Path parent = assets.relativize(f.toAbsolutePath().normalize()).getParent();
            String folder = parent == null ? "" : parent.toString().replace('\\', '/');
            ExportState.Entry before = old.sources().get(path);
            Exporter.Source src;
            try {
                src = Exporter.read(f, path, folder, palettes, config);
            } catch (ParseException e) {
                failed++;
                out.println("FAIL " + path + " (its files in the Godot project are left as they were)");
                for (String error : e.errors()) SpriteFiles.printError(out, error);
                if (before != null) sources.put(path, before);
                continue;
            }
            if (!all && before != null && before.hash().equals(src.hash()) && allExist(config, before.outputs())) {
                unchanged++;
                sources.put(path, before);
                warnShared(before.outputs(), path, writtenBy, out);
                continue;
            }
            Exporter.Result r;
            try {
                r = Exporter.export(src, config);
            } catch (IllegalArgumentException e) {
                failed++;
                out.println("FAIL " + path + ": " + e.getMessage());
                if (before != null) sources.put(path, before);
                continue;
            }
            exported++;
            sources.put(path, new ExportState.Entry(src.hash(), r.outputs()));
            String dir = r.outputs().getFirst();
            dir = dir.contains("/") ? dir.substring(0, dir.lastIndexOf('/')) : "";
            out.println("exported " + path + " -> res://" + dir + ": "
                    + String.join(", ", r.names()));
            for (String w : r.warnings()) out.println("     warning: " + w);
            warnShared(r.outputs(), path, writtenBy, out);
        }

        // Files written before that no source writes now.
        Set<String> now = new LinkedHashSet<>();
        sources.values().forEach(e -> now.addAll(e.outputs()));
        List<String> gone = new ArrayList<>();
        old.sources().values().forEach(e -> e.outputs().stream().filter(o -> !now.contains(o)).forEach(gone::add));
        int removed = 0;
        for (String o : gone) {
            Path p = config.project().resolve(o).normalize();
            if (!p.startsWith(config.project())) continue;  // never outside the Godot project
            boolean deleted = Files.deleteIfExists(p);
            Files.deleteIfExists(p.resolveSibling(p.getFileName() + ".import"));
            if (deleted) {
                removed++;
                out.println("removed res://" + o);
            }
        }

        new ExportState(config.project(), sources).save(statePath);
        out.println("done: " + exported + " exported, " + unchanged + " unchanged, " + removed + " removed, "
                + failed + " failed (Godot project " + config.project().toString().replace('\\', '/') + ")");
        return failed == 0 ? 0 : 1;
    }

    /**
     * Records which source writes each output, and warns when another source writes it too,
     * whether either of them was exported this time or not.
     */
    private static void warnShared(List<String> outputs, String path, Map<String, String> writtenBy,
                                   PrintStream out) {
        for (String o : outputs) {
            String other = writtenBy.putIfAbsent(o, path);
            if (other != null && !other.equals(path)) {
                out.println("warning: res://" + o + " is written by both " + other + " and " + path
                        + "; give one of them another name");
            }
        }
    }

    private static boolean allExist(ExportConfig config, List<String> outputs) {
        for (String o : outputs) if (!Files.isRegularFile(config.project().resolve(o))) return false;
        return true;
    }
}
