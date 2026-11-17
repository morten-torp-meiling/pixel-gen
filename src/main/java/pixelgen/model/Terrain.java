package pixelgen.model;

import java.util.List;
import java.util.Map;

/**
 * Where two grounds meet: a raised ground (grass) and a lower one (dirt), the boundary profiles
 * between them, and the light rule that shades the boundary. One terrain makes every transition
 * tile: each tile picks the profiles that shape its patch of raised ground.
 * <p>
 * A profile says, per column (for {@code n}, {@code s}) or per row ({@code w}, {@code e}), how many
 * pixels of raised ground reach in from that side of the tile. A profile with the same value at
 * both ends lets its tile sit next to a copy of itself; profiles that share their end values let
 * edges, corners and inner corners meet.
 * <p>
 * The raised ground stands slightly higher. With the light from the top left:
 * <ul>
 *   <li>{@code bank}: raised ground with lower ground below it or to its right (a face turned
 *       away from the light);</li>
 *   <li>{@code lip}: raised ground with lower ground above it or to its left (an edge catching
 *       the light);</li>
 *   <li>{@code shadow}: lower ground just below or right of raised ground (the shade it casts).</li>
 * </ul>
 * Light from another corner mirrors these sides: from the top right, the bank faces down and left.
 *
 * @param name     the terrain's name
 * @param raised   the raised ground's texture, a full tile
 * @param lower    the lower ground's texture, a full tile
 * @param bank     symbol for the bank, or 0 for none
 * @param lip      symbol for the lit lip, or 0 for none
 * @param shadow   symbol for the cast shadow, or 0 for none
 * @param profiles by name; the first letter (n, s, w, e) is the side
 * @param light    where the light comes from
 */
public record Terrain(String name, Grid raised, Grid lower, char bank, char lip, char shadow,
                      Map<String, int[]> profiles, Light light) {

    public Terrain {
        profiles = Map.copyOf(profiles);
    }

    /** A terrain lit from the top left. */
    public Terrain(String name, Grid raised, Grid lower, char bank, char lip, char shadow,
                   Map<String, int[]> profiles) {
        this(name, raised, lower, bank, lip, shadow, profiles, Light.TOP_LEFT);
    }

    /** The same terrain lit from {@code newLight}. */
    public Terrain withLight(Light newLight) {
        return new Terrain(name, raised, lower, bank, lip, shadow, profiles, newLight);
    }

    /**
     * The tile for a combination of profiles: raised ground where any of them reaches
     * ({@code union}: an outer corner), or where all of them reach (an inner corner).
     */
    public Grid blend(List<String> names, boolean union) {
        int w = raised.width(), h = raised.height();
        // Steps towards the light (lx, ly) and away from it (-lx, -ly).
        int lx = light.lx(), ly = light.ly();
        char[] out = new char[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c;
                if (isRaised(names, union, x, y)) {
                    if (bank != 0 && (!isRaised(names, union, x, y - ly) || !isRaised(names, union, x - lx, y))) {
                        c = bank;
                    } else if (lip != 0 && (!isRaised(names, union, x, y + ly) || !isRaised(names, union, x + lx, y))) {
                        c = lip;
                    } else {
                        c = raised.at(x, y);
                    }
                } else {
                    if (shadow != 0 && (isRaised(names, union, x, y + ly) || isRaised(names, union, x + lx, y))) {
                        c = shadow;
                    } else {
                        c = lower.at(x, y);
                    }
                }
                out[y * w + x] = c;
            }
        }
        return new Grid(w, h, out);
    }

    /**
     * Whether a point is raised ground. Points just outside the tile follow the same profiles
     * (wrapping around), so the boundary is shaded as if the tile repeated, like its neighbours.
     */
    boolean isRaised(List<String> names, boolean union, int x, int y) {
        boolean any = false, all = true;
        for (String n : names) {
            boolean r = reaches(n, x, y);
            any |= r;
            all &= r;
        }
        return union ? any : all;
    }

    private boolean reaches(String profile, int x, int y) {
        int[] d = profiles.get(profile);
        int w = raised.width(), h = raised.height();
        return switch (profile.charAt(0)) {
            case 'n' -> y < d[Math.floorMod(x, w)];
            case 's' -> y >= h - d[Math.floorMod(x, w)];
            case 'w' -> x < d[Math.floorMod(y, h)];
            case 'e' -> x >= w - d[Math.floorMod(y, h)];
            default -> throw new IllegalArgumentException("profile '" + profile + "' doesn't start with n, s, w or e");
        };
    }
}
