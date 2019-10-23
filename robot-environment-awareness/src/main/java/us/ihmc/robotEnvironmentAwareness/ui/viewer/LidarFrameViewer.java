package us.ihmc.robotEnvironmentAwareness.ui.viewer;

import controller_msgs.msg.dds.LidarScanMessage;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.javaFXToolkit.JavaFXTools;
import us.ihmc.messager.MessagerAPIFactory.Topic;
import us.ihmc.robotEnvironmentAwareness.communication.REAUIMessager;

public class LidarFrameViewer extends AbstractSensorFrameViewer<LidarScanMessage>
{
   public LidarFrameViewer(REAUIMessager uiMessager, Topic<LidarScanMessage> messageState)
   {
      super(uiMessager, messageState);
   }

   @Override
   public void handleMessage(LidarScanMessage message)
   {
      if (message == null)
         return;
      Quaternion orientation = message.getLidarOrientation();
      Point3D position = message.getLidarPosition();
      lastAffine.set(JavaFXTools.createAffineFromQuaternionAndTuple(orientation, position));
   }
}
