package com.local.pp_backen.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a PDF into one PNG per page.
 *
 * <p>Rasterising sidesteps the broken-font problem completely: a paper typeset with a
 * legacy Sinhala font has a text layer full of Latin gibberish, but the rendered page
 * shows exactly what a human sees, which is what a vision model needs. It is also the
 * only way to get at diagrams, which have no text layer at all.
 */
@Service
public class PdfRasterizer {

    private static final Logger log = LoggerFactory.getLogger(PdfRasterizer.class);

    private final int dpi;
    private final int maxPages;

    public PdfRasterizer(@Value("${app.import.dpi:200}") int dpi,
                         @Value("${app.import.max-pages:40}") int maxPages) {
        this.dpi = dpi;
        this.maxPages = maxPages;
    }

    /** One rendered page: PNG bytes plus the pixel size, needed later to crop figures. */
    public record RenderedPage(int number, byte[] png, int width, int height) {
    }

    /** Reports how far rendering has got, so a slow PDF does not look like a hang. */
    public interface Progress {
        void page(int done, int total);
    }

    /**
     * Accepts an image as a single page, so a photographed or screenshotted paper
     * can be imported without first being wrapped in a PDF. Detected from the
     * file's own magic bytes rather than its name, which is easily wrong.
     */
    public List<RenderedPage> rasteriseAny(byte[] bytes, String fileName, Progress progress) {
        if (looksLikePdf(bytes)) {
            return rasterise(bytes, progress);
        }

        progress.page(0, 1);
        try {
            BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException(
                        "\"" + fileName + "\" is neither a PDF nor an image this server can read. "
                        + "PDF, PNG, JPEG and WebP are supported.");
            }
            // The vision call is billed per page either way, so there is nothing to
            // gain from splitting an image up; it goes through as one page.
            List<RenderedPage> pages = List.of(
                    new RenderedPage(1, toPng(image), image.getWidth(), image.getHeight()));
            progress.page(1, 1);
            return pages;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("That image could not be read: " + e.getMessage(), e);
        }
    }

    /** %PDF- at the start of the file. */
    private static boolean looksLikePdf(byte[] bytes) {
        return bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    public List<RenderedPage> rasterise(byte[] pdfBytes, Progress progress) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new IllegalArgumentException("That PDF has no pages.");
            }
            if (pageCount > maxPages) {
                throw new IllegalArgumentException(
                        "That PDF has " + pageCount + " pages; the importer accepts up to " + maxPages + ".");
            }

            progress.page(0, pageCount);

            PDFRenderer renderer = new PDFRenderer(document);
            List<RenderedPage> pages = new ArrayList<>(pageCount);

            for (int i = 0; i < pageCount; i++) {
                long started = System.currentTimeMillis();
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                pages.add(new RenderedPage(i + 1, toPng(image), image.getWidth(), image.getHeight()));

                long took = System.currentTimeMillis() - started;
                if (took > 5000) {
                    log.warn("Page {} took {} ms to render — the PDF may use heavy image encodings",
                            i + 1, took);
                }
                progress.page(i + 1, pageCount);
            }

            log.info("Rasterised {} page(s) at {} DPI", pages.size(), dpi);
            return pages;

        } catch (IOException e) {
            throw new IllegalArgumentException("That file could not be read as a PDF.", e);
        }
    }

    /**
     * Cuts a region out of a rendered page. The box is given in fractions of the page
     * (0–1), which is what a vision model can report reliably — asking for pixels gives
     * boxes that are off by enough to slice a graph in half.
     *
     * @param padding extra fraction of the page added on every side, because tick labels
     *                and the "(1)" caption usually sit just outside the drawn area
     */
    public byte[] crop(RenderedPage page, double x0, double y0, double x1, double y1, double padding) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(page.png()));

            int left = clamp((x0 - padding) * image.getWidth(), 0, image.getWidth() - 1);
            int top = clamp((y0 - padding) * image.getHeight(), 0, image.getHeight() - 1);
            int right = clamp((x1 + padding) * image.getWidth(), left + 1, image.getWidth());
            int bottom = clamp((y1 + padding) * image.getHeight(), top + 1, image.getHeight());

            BufferedImage cropped = image.getSubimage(left, top, right - left, bottom - top);
            return toPng(cropped);

        } catch (IOException e) {
            throw new IllegalStateException("Could not crop the page image", e);
        }
    }

    /**
     * Re-encodes a rendered page as JPEG for sending to the model. A 200 DPI page is
     * roughly 1.6 MB as PNG but around 250 KB as JPEG, which keeps request sizes and
     * latency down without costing legibility at the sizes text is printed.
     */
    public byte[] toJpeg(byte[] png, float quality) {
        try {
            BufferedImage source = ImageIO.read(new java.io.ByteArrayInputStream(png));

            // JPEG has no alpha channel, so flatten onto white first
            BufferedImage flat = new BufferedImage(
                    source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = flat.createGraphics();
            g.drawImage(source, 0, 0, java.awt.Color.WHITE, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(flat, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();

        } catch (IOException e) {
            // Falling back to the PNG is always safe, just larger
            log.warn("Could not convert a page to JPEG, sending the PNG instead: {}", e.getMessage());
            return png;
        }
    }

    private static int clamp(double value, int min, int max) {
        return (int) Math.max(min, Math.min(max, Math.round(value)));
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
