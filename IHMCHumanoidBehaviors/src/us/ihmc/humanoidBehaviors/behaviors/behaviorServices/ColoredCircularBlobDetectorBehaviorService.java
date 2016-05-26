package us.ihmc.humanoidBehaviors.behaviors.behaviorServices;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.vecmath.Point2d;

import us.ihmc.communication.producers.JPEGCompressor;
import us.ihmc.communication.producers.JPEGDecompressor;
import us.ihmc.communication.producers.VideoSource;
import us.ihmc.humanoidBehaviors.behaviors.BehaviorInterface;
import us.ihmc.humanoidBehaviors.communication.ConcurrentListeningQueue;
import us.ihmc.humanoidRobotics.communication.packets.sensing.VideoPacket;
import us.ihmc.ihmcPerception.OpenCVTools;
import us.ihmc.ihmcPerception.vision.shapes.HSVRange;
import us.ihmc.ihmcPerception.vision.shapes.HoughCircleResult;
import us.ihmc.ihmcPerception.vision.shapes.OpenCVColoredCircularBlobDetector;
import us.ihmc.ihmcPerception.vision.shapes.OpenCVColoredCircularBlobDetectorFactory;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.sensorProcessing.communication.packets.dataobjects.RobotConfigurationData;
import us.ihmc.tools.thread.ThreadTools;

public class ColoredCircularBlobDetectorBehaviorService extends ThreadedBehaviorService
{
   private final ConcurrentListeningQueue<VideoPacket> videoPacketQueue = new ConcurrentListeningQueue<>();
   private final ConcurrentListeningQueue<RobotConfigurationData> robotConfigurationDataQueue = new ConcurrentListeningQueue<>();
   private long videoTimestamp = -1L;
   
   private final JPEGDecompressor jpegDecompressor = new JPEGDecompressor();
   private final JPEGCompressor jpegCompressor = new JPEGCompressor();
   
   private final OpenCVColoredCircularBlobDetector openCVColoredCircularBlobDetector;
   private final Point2d latestBallPosition2d = new Point2d();
   private BufferedImage latestUnmodifiedCameraImage;
   
   public ColoredCircularBlobDetectorBehaviorService(BehaviorInterface behaviorInterface)
   {
      super(ColoredCircularBlobDetectorBehaviorService.class.getSimpleName(), behaviorInterface);
      
      getBehaviorInterface().attachNetworkProcessorListeningQueue(videoPacketQueue, VideoPacket.class);
      getBehaviorInterface().attachNetworkProcessorListeningQueue(robotConfigurationDataQueue, RobotConfigurationData.class);
      
      OpenCVColoredCircularBlobDetectorFactory factory = new OpenCVColoredCircularBlobDetectorFactory();
      factory.setCaptureSource(OpenCVColoredCircularBlobDetector.CaptureSource.JAVA_BUFFERED_IMAGES);
      openCVColoredCircularBlobDetector = factory.buildBlobDetector();
   }

   @Override
   public void doThreadAction()
   {
      if (videoPacketQueue.isNewPacketAvailable())
      {
         VideoPacket videoPacket = videoPacketQueue.getLatestPacket();
         RobotConfigurationData robotConfigurationData = robotConfigurationDataQueue.getLatestPacket();
         videoTimestamp = robotConfigurationData.getTimestamp();

         latestUnmodifiedCameraImage = jpegDecompressor.decompressJPEGDataToBufferedImage(videoPacket.getData());

         openCVColoredCircularBlobDetector.updateFromBufferedImage(latestUnmodifiedCameraImage);
         ArrayList<HoughCircleResult> circles = openCVColoredCircularBlobDetector.getCircles();

         BufferedImage thresholdBufferedImageOpenCVEncoded = OpenCVTools.convertMatToBufferedImage(openCVColoredCircularBlobDetector.getThresholdMat());
         BufferedImage thresholdBufferedImage = OpenCVTools.convertToCompressableBufferedImage(thresholdBufferedImageOpenCVEncoded);

         byte[] jpegThresholdImage = jpegCompressor.convertBufferedImageToJPEGData(thresholdBufferedImage);
         VideoPacket circleBlobThresholdImagePacket = new VideoPacket(RobotSide.LEFT, VideoSource.CV_THRESHOLD, videoTimestamp, jpegThresholdImage,
                                                                      videoPacket.getPosition(), videoPacket.getOrientation(), videoPacket.getIntrinsicParameters());
         getBehaviorInterface().sendPacketToNetworkProcessor(circleBlobThresholdImagePacket);

         if (circles.size() > 0)
            latestBallPosition2d.set(circles.get(0).getCenter());
      }
      else
      {
         ThreadTools.sleep(10);
      }
   }
   
   public void addHSVRange(HSVRange hsvRange)
   {
      openCVColoredCircularBlobDetector.addHSVRange(hsvRange);
   }

   public Point2d getLatestBallPosition2d()
   {
      return latestBallPosition2d;
   }
   
   public BufferedImage getLatestUnmodifiedCameraImage()
   {
      return latestUnmodifiedCameraImage;
   }

   public void clearHSVRanges()
   {
      openCVColoredCircularBlobDetector.resetRanges();
   }
}
