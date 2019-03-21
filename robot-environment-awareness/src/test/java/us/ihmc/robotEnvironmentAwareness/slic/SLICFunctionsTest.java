package us.ihmc.robotEnvironmentAwareness.slic;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class SLICFunctionsTest
{
   public SLICFunctionsTest() throws IOException
   {
      BufferedImage originalImage = ImageIO.read(new File("LevinCenter.jpeg"));
      //BufferedImage originalImage = ImageIO.read(new File("IHMC_Trials.jpg"));
      //BufferedImage originalImage = ImageIO.read(new File("Saved_Image_0.png"));
      int width = originalImage.getWidth();
      int height = originalImage.getHeight();
      showImage(originalImage, "original");

      BufferedImage argbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int i = 0; i < width; i++)
         for (int j = 0; j < height; j++)
            argbImage.setRGB(i, j, originalImage.getRGB(i, j));
      showImage(argbImage, "argb");

      int k = 2000;
      double m = 20;
      int numberOfLabels = 0;
      int[] kLabels = new int[width * height];
      int[] imgBuff = new int[width * height];

      int imageBuffIndex = 0;
      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            imgBuff[imageBuffIndex] = argbImage.getRGB(j, i) + 0xFF000000;
            imageBuffIndex++;
         }
      }

      SLICFunctions.performSLICOForGivenK(imgBuff, width, height, kLabels, numberOfLabels, k, m);

      int contourColor = 0xFFFF0000;
      SLICFunctions.drawContoursAroundSegments(imgBuff, kLabels, width, height, contourColor);

      BufferedImage segmentedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      int segmentedImageBuffIndex = 0;
      for (int i = 0; i < height; i++)
      {
         for (int j = 0; j < width; j++)
         {
            segmentedImage.setRGB(j, i, imgBuff[segmentedImageBuffIndex]);
            segmentedImageBuffIndex++;
         }
      }
      showImage(segmentedImage, "segmented");
   }

   private void showImage(BufferedImage bufferedImage, String title)
   {
      int width = bufferedImage.getWidth();
      int height = bufferedImage.getHeight();

      JFrame frame = new JFrame(title);
      JPanel panel = new JPanel();
      JLabel label = new JLabel(new ImageIcon(bufferedImage));
      panel.add(label);

      Dimension dim = new Dimension(width, height);

      frame.setPreferredSize(dim);
      frame.add(panel);
      frame.setLocation(200, 100);
      frame.pack();
      frame.setVisible(true);

      System.out.println("getNumDataElements " + bufferedImage.getData().getNumDataElements());
      System.out.println("getNumBands " + bufferedImage.getData().getNumBands());
      System.out.println("getType " + bufferedImage.getType());
   }

   public static void main(String[] args) throws IOException
   {
      SLICFunctionsTest test = new SLICFunctionsTest();
   }
}
