package us.ihmc.robotics.geometry.shapes;

import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.shape.Sphere3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DBasics;

public class FrameSphere3d extends FrameShape3d<FrameSphere3d, Sphere3D>
{
   private final Sphere3D sphere;
   
   public FrameSphere3d()
   {
      super(new Sphere3D());
      sphere = getGeometryObject();
   }
   
   public FrameSphere3d(ReferenceFrame referenceFrame)
   {
      super(referenceFrame, new Sphere3D());
      sphere = getGeometryObject();
   }
   
   public FrameSphere3d(ReferenceFrame referenceFrame, double x, double y, double z, double radius)
   {
      super(referenceFrame, new Sphere3D(x, y, z, radius));
      sphere = getGeometryObject();
   }
   
   public Sphere3D getSphere3d()
   {
      return sphere;
   }
   
   public void getCenter(FramePoint3D centerToPack)
   {
      centerToPack.setToZero(getReferenceFrame());
      sphere.getPosition(centerToPack);
   }
   
   public void getCenter(Point3DBasics centerToPack)
   {
      sphere.getPosition(centerToPack);
   }
   
   public double getRadius()
   {
      return sphere.getRadius();
   }
   
   public void setRadius(double radius)
   {
      sphere.setRadius(radius);
   }
   
   public void setIncludingFrame(ReferenceFrame referenceFrame, double radius)
   {
      setToZero(referenceFrame);
      setRadius(radius);
   }
}
