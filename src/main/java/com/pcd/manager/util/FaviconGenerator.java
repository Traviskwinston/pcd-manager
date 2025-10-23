package com.pcd.manager.util;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates favicon assets from a provided source image placed at
 * src/main/resources/static/favicon-source.png.
 *
 * On application startup (dev or prod), if the destination favicon files are
 * missing, this will crop black edges from the source and export:
 * - favicon-16.png
 * - favicon-32.png
 * - favicon-48.png
 * - apple-touch-icon.png (180x180)
 * - favicon.ico (ICO container with 16px and 32px PNGs)
 */
@Component
public class FaviconGenerator {

    private static final String STATIC_DIR = "src/main/resources/static";
    private static final Path SOURCE_PATH = Paths.get(STATIC_DIR, "favicon-source.png");

    @EventListener(ApplicationReadyEvent.class)
    public void generateFaviconsOnStartup() {
        try {
            if (!Files.exists(SOURCE_PATH)) {
                return; // no source provided; nothing to do
            }

            Path out16 = Paths.get(STATIC_DIR, "favicon-16.png");
            Path out32 = Paths.get(STATIC_DIR, "favicon-32.png");
            Path out48 = Paths.get(STATIC_DIR, "favicon-48.png");
            Path out180 = Paths.get(STATIC_DIR, "apple-touch-icon.png");
            Path outIco = Paths.get(STATIC_DIR, "favicon.ico");

            // Only regenerate if any of the outputs are missing
            if (Files.exists(out16) && Files.exists(out32) && Files.exists(out48) && Files.exists(out180) && Files.exists(outIco)) {
                return;
            }

            BufferedImage source = ImageIO.read(SOURCE_PATH.toFile());
            if (source == null) return;

            BufferedImage cropped = cropBlackEdges(source);
            if (cropped == null) cropped = source;
            // Remove white/black matte connected to edges (transparent corners)
            cropped = removeEdgeMatteToTransparent(cropped);
            // Remove remaining white halos near transparent areas
            cropped = removeWhiteHaloNearTransparency(cropped, 2, 235);

            BufferedImage img16 = resize(cropped, 16, 16);
            BufferedImage img32 = resize(cropped, 32, 32);
            BufferedImage img48 = resize(cropped, 48, 48);
            BufferedImage img180 = resize(cropped, 180, 180);

            ImageIO.write(img16, "PNG", out16.toFile());
            ImageIO.write(img32, "PNG", out32.toFile());
            ImageIO.write(img48, "PNG", out48.toFile());
            ImageIO.write(img180, "PNG", out180.toFile());

            // Write ICO using PNG entries (Windows supports PNG-compressed icons)
            writePngIco(outIco, List.of(img16, img32));
        } catch (Exception ignore) {
            // Non-critical; favicon generation should never break the app
        }
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    /**
     * Flood-fills from all image edges and turns any near-white or near-black
     * connected background pixels fully transparent. This removes visible white
     * corners around rounded logos.
     */
    private static BufferedImage removeEdgeMatteToTransparent(BufferedImage src) {
        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.drawImage(src, 0, 0, null);
            g2.dispose();
            src = argb;
        }

        final int w = src.getWidth();
        final int h = src.getHeight();
        boolean[][] visited = new boolean[h][w];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();

        // Seed queue with all border pixels
        for (int x = 0; x < w; x++) {
            q.add(new int[]{x, 0});
            q.add(new int[]{x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            q.add(new int[]{0, y});
            q.add(new int[]{w - 1, y});
        }

        final int whiteThresh = 240; // near white
        final int blackThresh = 20;  // near black

        while (!q.isEmpty()) {
            int[] p = q.pollFirst();
            int x = p[0], y = p[1];
            if (x < 0 || y < 0 || x >= w || y >= h) continue;
            if (visited[y][x]) continue;
            visited[y][x] = true;

            int argb = src.getRGB(x, y);
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;

            boolean nearWhite = a == 0 || (r >= whiteThresh && g >= whiteThresh && b >= whiteThresh);
            boolean nearBlack = (r <= blackThresh && g <= blackThresh && b <= blackThresh);

            if (nearWhite || nearBlack) {
                // Make transparent and continue flood fill
                src.setRGB(x, y, 0x00000000);
                q.add(new int[]{x + 1, y});
                q.add(new int[]{x - 1, y});
                q.add(new int[]{x, y + 1});
                q.add(new int[]{x, y - 1});
            }
        }

        return src;
    }

    /**
     * Iteratively removes near-white halo pixels that border transparent pixels.
     * iterations: number of erosion passes; whiteThreshold: per-channel min to consider near-white.
     */
    private static BufferedImage removeWhiteHaloNearTransparency(BufferedImage src, int iterations, int whiteThreshold) {
        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.drawImage(src, 0, 0, null);
            g2.dispose();
            src = argb;
        }

        final int w = src.getWidth();
        final int h = src.getHeight();
        int[] dx = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dy = {0, 0, 1, -1, 1, -1, 1, -1};

        for (int pass = 0; pass < iterations; pass++) {
            List<int[]> toClear = new ArrayList<>();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = src.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    if (a == 0) continue;
                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    boolean nearWhite = r >= whiteThreshold && g >= whiteThreshold && b >= whiteThreshold;
                    if (!nearWhite) continue;

                    // Check for any transparent neighbor
                    boolean touchesTransparent = false;
                    for (int k = 0; k < dx.length; k++) {
                        int nx = x + dx[k];
                        int ny = y + dy[k];
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int na = (src.getRGB(nx, ny) >>> 24) & 0xFF;
                        if (na == 0) { touchesTransparent = true; break; }
                    }
                    if (touchesTransparent) toClear.add(new int[]{x, y});
                }
            }
            for (int[] p : toClear) src.setRGB(p[0], p[1], 0x00000000);
            if (toClear.isEmpty()) break; // nothing more to erode
        }

        return src;
    }

    /**
     * Crops uniform black borders from the image edges.
     * Treats any pixel with RGB close to black (<= 20 for each channel) as black.
     */
    private static BufferedImage cropBlackEdges(BufferedImage src) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        int top = 0, left = 0, right = w - 1, bottom = h - 1;

        // Helper to check if a row/column is all black/transparent-ish
        final int threshold = 20;
        // top
        while (top <= bottom && isBlackRow(src, top, threshold)) top++;
        // bottom
        while (bottom >= top && isBlackRow(src, bottom, threshold)) bottom--;
        // left
        while (left <= right && isBlackCol(src, left, threshold, top, bottom)) left++;
        // right
        while (right >= left && isBlackCol(src, right, threshold, top, bottom)) right--;

        if (left >= right || top >= bottom) return src;
        return src.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private static boolean isBlackRow(BufferedImage img, int y, int threshold) {
        int w = img.getWidth();
        for (int x = 0; x < w; x++) {
            int argb = img.getRGB(x, y);
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            if (a > 10 && (r > threshold || g > threshold || b > threshold)) return false;
        }
        return true;
    }

    private static boolean isBlackCol(BufferedImage img, int x, int threshold, int top, int bottom) {
        for (int y = top; y <= bottom; y++) {
            int argb = img.getRGB(x, y);
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >>> 16) & 0xFF;
            int g = (argb >>> 8) & 0xFF;
            int b = argb & 0xFF;
            if (a > 10 && (r > threshold || g > threshold || b > threshold)) return false;
        }
        return true;
    }

    /**
     * Writes a simple ICO file that contains PNG-compressed icon images.
     */
    private static void writePngIco(Path out, List<BufferedImage> images) throws IOException {
        List<byte[]> pngBytesList = new ArrayList<>();
        for (BufferedImage img : images) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            pngBytesList.add(baos.toByteArray());
        }

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))) {
            // ICONDIR
            dos.writeShort(0);      // reserved
            dos.writeShort(1);      // type 1 = icon
            dos.writeShort(pngBytesList.size());

            int offset = 6 + (16 * pngBytesList.size());
            for (int i = 0; i < pngBytesList.size(); i++) {
                BufferedImage img = images.get(i);
                byte[] data = pngBytesList.get(i);
                int w = img.getWidth();
                int h = img.getHeight();
                dos.writeByte(w == 256 ? 0 : w);  // width (0 means 256)
                dos.writeByte(h == 256 ? 0 : h);  // height
                dos.writeByte(0);                 // color count
                dos.writeByte(0);                 // reserved
                dos.writeShort(0);                // planes (0 for PNG data)
                dos.writeShort(0);                // bit count (0 for PNG data)
                dos.writeInt(data.length);        // bytes in resource
                dos.writeInt(offset);             // offset from beginning
                offset += data.length;
            }

            // Write image data blocks
            for (byte[] data : pngBytesList) {
                dos.write(data);
            }
        }
    }
}


