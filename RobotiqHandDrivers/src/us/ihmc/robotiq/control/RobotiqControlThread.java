package us.ihmc.robotiq.control;

import java.io.IOException;

import us.ihmc.commonWalkingControlModules.packetConsumers.FingerStateProvider;
import us.ihmc.communication.configuration.NetworkParameterKeys;
import us.ihmc.communication.configuration.NetworkParameters;
import us.ihmc.communication.packets.dataobjects.FingerState;
import us.ihmc.communication.packets.manipulation.FingerStatePacket;
import us.ihmc.communication.packets.manipulation.ManualHandControlPacket;
import us.ihmc.darpaRoboticsChallenge.handControl.HandControlThread;
import us.ihmc.darpaRoboticsChallenge.handControl.packetsAndConsumers.HandJointAngleCommunicator;
import us.ihmc.darpaRoboticsChallenge.handControl.packetsAndConsumers.ManualHandControlProvider;
import us.ihmc.robotiq.RobotiqHandInterface;
import us.ihmc.robotiq.data.RobotiqHandSensorData;
import us.ihmc.utilities.ThreadTools;
import us.ihmc.utilities.robotSide.RobotSide;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;

class RobotiqControlThread extends HandControlThread
{
   private final RobotSide robotSide;
   private final RobotiqHandInterface robotiqHand;
   private final FingerStateProvider fingerStateProvider;
   private final ManualHandControlProvider manualHandControlProvider;
   private final HandJointAngleCommunicator jointAngleCommunicator;
   private int errorCount = 0;
   private RobotiqHandSensorData handStatus;

   public RobotiqControlThread(RobotSide robotSide)
   {
      super(robotSide);
      this.robotSide = robotSide;
      robotiqHand = new RobotiqHandInterface(robotSide.equals(RobotSide.LEFT) ? NetworkParameters.getHost(NetworkParameterKeys.leftHand) : NetworkParameters.getHost(NetworkParameterKeys.rightHand));
      fingerStateProvider = new FingerStateProvider(robotSide);
      manualHandControlProvider = new ManualHandControlProvider(robotSide);
      jointAngleCommunicator = new HandJointAngleCommunicator(robotSide, packetCommunicator);
      
      packetCommunicator.attachListener(FingerStatePacket.class, fingerStateProvider);
      packetCommunicator.attachListener(ManualHandControlPacket.class, manualHandControlProvider);
   }

   public void connect()
   {
      try
      {
         packetCommunicator.connect();
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      
      while(!packetCommunicator.isConnected())
      {
         ThreadTools.sleep(100);
      }
   }

   public void initialize()
   {
      errorCount = 0;
      int faultCounter = 0;
      do
      {
         if (++errorCount > 3)
         {
            System.out.println("Unable to initalize " + robotSide.toString() + " Hand. Uncorrectable Fault has occurred");
            return;
         }
         do
         {
            robotiqHand.initialize();
            boolean initialized = true;
            do
            {
               ThreadTools.sleep(100);
               try
               {
                  updateHandData();
               }
               catch (IOException e)
               {
                  e.printStackTrace();
                  initialized = false;
               }
            }
            while (handStatus.isInitializing() && !handStatus.hasError() && !initialized);

            if (handStatus.hasError())
            {
               handStatus.printError();
               faultCounter++;
               if (faultCounter < 3)
                  robotiqHand.reset();
               else
               {
                  break;
               }
            }

         }
         while (handStatus.hasCompletedAction());

         ThreadTools.sleep(100);
      }
      while (!robotiqHand.isReady());

      System.out.println(robotSide.toString() + " Hand Set Up");
   }

   private void updateHandData() throws IOException
   {
      handStatus = robotiqHand.getHandStatus();
      jointAngleCommunicator.updateHandAngles(handStatus);
      jointAngleCommunicator.write();
   }

   public void run()
   {
      fingerStateProvider.receivedPacket(new FingerStatePacket(robotSide, FingerState.CALIBRATE));

      while (packetCommunicator.isConnected())
      {
         if (robotiqHand.isConnected()) //status to UI and keep alive packet
         {
            robotiqHand.doControl();

            try
            {
               updateHandData();
            }
            catch (IOException e)
            {
               continue;
            }

            if (handStatus.hasError())
               handStatus.printError();

            if (fingerStateProvider.isNewFingerStateAvailable())
            {
               FingerStatePacket packet = fingerStateProvider.pullPacket();
               FingerState state = packet.getFingerState();
               if (!robotiqHand.isConnected())
               {
                  if (state.equals(FingerState.CALIBRATE))
                  {
                     this.initialize();
                  }
                  else
                  {
                     System.out.println(robotSide.toString() + " Hand Not Connected");
                     continue;
                  }
               }
               
               switch (state)
               {
               case CALIBRATE:
                  robotiqHand.initialize();
                  break;
               case STOP:
                  robotiqHand.stop();
                  break;
               case OPEN:
                  robotiqHand.open();
                  break;
               case CLOSE:
                  robotiqHand.close();
                  break;
               case CRUSH:
                  robotiqHand.crush();
                  break;
               case HOOK:
                  robotiqHand.hook(robotSide);
                  break;
               case BASIC_GRIP:
                  robotiqHand.normalGrip();
                  break;
               case PINCH_GRIP:
                  robotiqHand.pinchGrip();
                  break;
               case WIDE_GRIP:
                  robotiqHand.wideGrip();
                  break;
               case SCISSOR_GRIP:
                  robotiqHand.scissorGrip();
                  break;
               case HALF_CLOSE:
                  System.out.println("RobotiqControlThread sending HALF_CLOSE");
                  robotiqHand.close(0.25);
                  break;
               case RESET:
               {
                  robotiqHand.reset();
                  initialize();
               }
               break;
               default:
                  break;
               }
            }
            
            if (manualHandControlProvider.isNewPacketAvailable()) // send manual hand control packet to hand
            {
               ManualHandControlPacket packet = manualHandControlProvider.pullPacket();
               if (!robotiqHand.isConnected() || !packet.getRobotSide().equals(robotSide))
                  continue;
               if (packet.getControlType() == ManualHandControlPacket.POSITION)
               {
                  robotiqHand.positionControl(packet.getCommands(ManualHandControlPacket.HandType.ROBOTIQ), this.robotSide);
               }
               else if (packet.getControlType() == ManualHandControlPacket.VELOCITY)
               {
                  robotiqHand.velocityControl(packet.getCommands(ManualHandControlPacket.HandType.ROBOTIQ), this.robotSide);
               }
            }
         }
         
         ThreadTools.sleep(50);
      }
   }
   
   public static void main(String[] args)
   {
      JSAP jsap = new JSAP();
      
      FlaggedOption robotSide = new FlaggedOption("robotSide").setRequired(true).setLongFlag("robotSide").setShortFlag('r').setStringParser(JSAP.STRING_PARSER);
      
      try
      {
         jsap.registerParameter(robotSide);
         JSAPResult config = jsap.parse(args);
         
         if(config.success())
         {
            RobotiqControlThread controlThread = new RobotiqControlThread(RobotSide.valueOf(config.getString("robotSide").toUpperCase()));
            controlThread.connect();
            controlThread.run();
         }
      }
      catch (JSAPException e)
      {
         e.printStackTrace();
      }
   }
}