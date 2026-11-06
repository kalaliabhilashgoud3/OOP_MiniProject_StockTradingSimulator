package com.stocksim.util;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the stock-chart PNG and Windows ICO app icons. */
public final class IconGenerator {
    private IconGenerator() { }

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(18, 45, 75));
        graphics.fillRoundRect(0, 0, 64, 64, 12, 12);
        graphics.setColor(new Color(40, 210, 150));
        graphics.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(9, 49, 24, 37);
        graphics.drawLine(24, 37, 35, 42);
        graphics.drawLine(35, 42, 54, 15);
        graphics.dispose();

        Path png = Path.of("icon.png");
        Path ico = Path.of("icon.ico");
        ImageIO.write(image, "png", png.toFile());
        byte[] pngBytes = Files.readAllBytes(png);
        ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        header.putShort((short) 0);   // Reserved field.
        header.putShort((short) 1);   // Icon resource type.
        header.putShort((short) 1);   // One image in this icon.
        header.put((byte) 64);        // Image width.
        header.put((byte) 64);        // Image height.
        header.put((byte) 0);         // Use the full color palette.
        header.put((byte) 0);
        header.putShort((short) 1);   // Color planes.
        header.putShort((short) 32);  // Bits per pixel.
        header.putInt(pngBytes.length);
        header.putInt(22);            // Image data starts after ICONDIR + ICONDIRENTRY.
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(ico))) {
            output.write(header.array());
            output.write(pngBytes);
        }
        System.out.println("Created icon.png and icon.ico");
    }
}
