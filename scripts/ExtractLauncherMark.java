import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class ExtractLauncherMark {
    private ExtractLauncherMark() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ExtractLauncherMark <input.png> <output.png>");
        }
        BufferedImage input = ImageIO.read(new File(args[0]));
        BufferedImage source = new BufferedImage(
            input.getWidth(),
            input.getHeight(),
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D sourceGraphics = source.createGraphics();
        sourceGraphics.drawImage(input, 0, 0, null);
        sourceGraphics.dispose();
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = 0;
        int maxY = 0;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                int maximum = Math.max(red, Math.max(green, blue));
                int minimum = Math.min(red, Math.min(green, blue));
                double saturation = maximum == 0 ? 0 : (double) (maximum - minimum) / maximum;
                double mask = minimum > 170
                    ? 0
                    : Math.min(1, Math.max(0, (saturation - 0.10) / 0.12));
                int alpha = (int) (((argb >>> 24) & 0xff) * mask);
                source.setRGB(x, y, (alpha << 24) | (argb & 0x00ffffff));
                if (alpha > 16) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (minX > maxX || minY > maxY) throw new IllegalStateException("No colored mark found");
        BufferedImage crop = source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        int outputSize = 432;
        int markSize = 250;
        double scale = Math.min((double) markSize / crop.getWidth(), (double) markSize / crop.getHeight());
        int targetWidth = (int) Math.round(crop.getWidth() * scale);
        int targetHeight = (int) Math.round(crop.getHeight() * scale);
        BufferedImage output = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(
            crop,
            (outputSize - targetWidth) / 2,
            (outputSize - targetHeight) / 2,
            targetWidth,
            targetHeight,
            null
        );
        graphics.dispose();
        ImageIO.write(output, "png", new File(args[1]));
    }
}
