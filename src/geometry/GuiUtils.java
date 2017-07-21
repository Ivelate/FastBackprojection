package geometry;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.lwjgl.util.vector.Vector3f;

/**
 *	Used to create the vertexes for each needed sphere recursions
 */
public class GuiUtils 
{
	public static final int SHADER_ARGS=3;
	public static int drawSphere(float centerx,float centery,float centerz,float radius,int recursions,FloatBuffer buffer,boolean halfSphere)
	{
		float[] floatbuff=new float[SHADER_ARGS];
		
		PointList pointList=new PointList(centerx,centery,centerz,radius);
		float au=(float)((1+Math.sqrt(5))/2);
		
		pointList.add(new Point(-1+centerx,  au+centery,  0+centerz));
		pointList.add(new Point( 1+centerx,  au+centery,  0+centerz));
		pointList.add(new Point(-1+centerx, -au+centery,  0+centerz));
		pointList.add(new Point( 1+centerx, -au+centery,  0+centerz));

		pointList.add(new Point( 0+centerx, -1+centery,  au+centerz));
		pointList.add(new Point( 0+centerx,  1+centery,  au+centerz));
		pointList.add(new Point( 0+centerx, -1+centery, -au+centerz));
		pointList.add(new Point( 0+centerx,  1+centery, -au+centerz));

		pointList.add(new Point( au+centerx,  0+centery, -1+centerz));
		pointList.add(new Point( au+centerx,  0+centery,  1+centerz));
		pointList.add(new Point(-au+centerx,  0+centery, -1+centerz));
		pointList.add(new Point(-au+centerx,  0+centery,  1+centerz));
		
		List<Ternary> vertList=new LinkedList<Ternary>();
		// 5 faces around point 0
		vertList.add(new Ternary(0, 11, 5,false));
		vertList.add(new Ternary(0, 5, 1,false));
		if(!halfSphere) vertList.add(new Ternary(0, 1, 7,false));
		if(!halfSphere) vertList.add(new Ternary(0, 7, 10,false));
		vertList.add(new Ternary(0, 10, 11,halfSphere));

		// 5 adjacent faces
		vertList.add(new Ternary(1, 5, 9,false));
		vertList.add(new Ternary(5, 11, 4,false));
		vertList.add(new Ternary(11, 10, 2,halfSphere));
		if(!halfSphere) vertList.add(new Ternary(10, 7, 6,false));
		if(!halfSphere) vertList.add(new Ternary(7, 1, 8,false));

		// 5 faces around point 3
		vertList.add(new Ternary(3, 9, 4,false));
		vertList.add(new Ternary(3, 4, 2,false));
		if(!halfSphere)  vertList.add(new Ternary(3, 2, 6,false));
		if(!halfSphere)  vertList.add(new Ternary(3, 6, 8,false));
		vertList.add(new Ternary(3, 8, 9,halfSphere));

		// 5 adjacent faces
		vertList.add(new Ternary(4, 9, 5,false));
		vertList.add(new Ternary(2, 4, 11,false));
		if(!halfSphere) vertList.add(new Ternary(6, 2, 10,false));
		if(!halfSphere) vertList.add(new Ternary(8, 6, 7,false));
		vertList.add(new Ternary(9, 8, 1,halfSphere));
		
		for (int i = 0; i < recursions; i++)
		{
		  List<Ternary> faces2 = new LinkedList<Ternary>();
		  for(Ternary t:vertList)
		  {
		      // replace triangle by 4 triangles
		      int pa = getMiddlePoint(t.x, t.y,pointList);
		      int pb = getMiddlePoint(t.y, t.z,pointList);
		      int pc = getMiddlePoint(t.z, t.x,pointList);
		      
		      if(t.dirty){
		    	  boolean xd=pointList.get(t.x).z<=0; 
		    	  boolean yd=pointList.get(t.y).z<=0; 
		    	  boolean zd=pointList.get(t.z).z<=0; 
		    	  boolean pad=pointList.get(pa).z<=0; 
		    	  boolean pbd=pointList.get(pb).z<=0;
		    	  boolean pcd=pointList.get(pc).z<=0;
		    	  if(!(xd&&pad&&pcd)) faces2.add(new Ternary(t.x, pa, pc,xd||pad||pcd));
		    	  if(!(yd&&pbd&&pad)) faces2.add(new Ternary(t.y, pb, pa,yd||pbd||pad));
		    	  if(!(zd&&pcd&&pbd)) faces2.add(new Ternary(t.z, pc, pb,zd||pcd||pbd));
		    	  if(!(pad&&pbd&&pcd)) faces2.add(new Ternary(pa, pb, pc,pad||pbd||pcd));
		      }
		      else{
		    	  faces2.add(new Ternary(t.x, pa, pc,false));
		    	  faces2.add(new Ternary(t.y, pb, pa,false));
		    	  faces2.add(new Ternary(t.z, pc, pb,false));
		    	  faces2.add(new Ternary(pa, pb, pc,false));
		      }
		  }
		  vertList = faces2;
		}

		//Upload to buffer bb
		int tric=0;
		for(Ternary t:vertList)
		  {
		      Point p1=pointList.get(t.x);
		      Point p2=pointList.get(t.y);
		      Point p3=pointList.get(t.z);
		      /*if(tric==0){ //Debug, sphere max approximation error
		    	  Vector3f center=new Vector3f((p1.x+p2.x+p3.x)/3,(p1.y+p2.y+p3.y)/3,(p1.z+p2.z+p3.z)/3);
		    	  System.out.print(","+(1-Math.sqrt(center.lengthSquared())));
		      }*/
		      
		      putTriangle(p1.x,p1.y,p1.z,p2.x,p2.y,p2.z,p3.x,p3.y,p3.z,floatbuff,buffer);
		      tric++;
		  }
		return tric;
	}
	private static int getMiddlePoint(int p1,int p2,PointList pl){
		Point po1=pl.get(p1);
		Point po2=pl.get(p2);
		
		Point mid=new Point((po1.x+po2.x)/2,(po1.y+po2.y)/2,(po1.z+po2.z)/2);
		return pl.add(mid);
	}
	
	public static void putTriangle(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,FloatBuffer fb){
		putTriangle(x1, y1, z1, x2, y2, z2, x3, y3, z3, new float[SHADER_ARGS], fb);
	}
	public static void putTriangle(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float[] buffer,FloatBuffer fb){
		buffer[0]=x1;buffer[1]=y1;buffer[2]=z1;
		
		/*buffer[3]= (y2-y1)*(z3-z1) - (z2-z1)*(y3-y1);
		buffer[4]= (z2-z1)*(x3-x1) - (x2-x1)*(z3-z1);
		buffer[5]= (x2-x1)*(y3-y1) - (y2-y1)*(x3-x1);*/
		
		fb.put(buffer);
		buffer[0]=x2;buffer[1]=y2;buffer[2]=z2;
		fb.put(buffer);
		buffer[0]=x3;buffer[1]=y3;buffer[2]=z3;
		fb.put(buffer);
	}
	
	
	private static class Ternary
	{
		public int x;public int y;public int z;public boolean dirty;

		public Ternary(int x,int y,int z,boolean dirty){
			this.x=x;this.y=y;this.z=z; this.dirty=dirty;
		}
	}
	private static class PointList
	{
		List<Point> pointList=new ArrayList<Point>();
		private float cx;
		private float cy;
		private float cz;
		private float radius;
		public PointList(float cx,float cy,float cz,float radius)
		{
			this.cx=cx;
			this.cy=cy;
			this.cz=cz;
			this.radius=radius;
		}
		public int contains(Point p)
		{
			int cont=0;
			for(Point pc:this.pointList)
			{
				if(p.equals(pc)) return cont;
				cont++;
			}
			return -1;
		}
		public int add(Point i)
		{
			//NORMALIZE TO RAD
			Point p=new Point(i.x,i.y,i.z);
			float distrat=radius/(float)Math.sqrt((p.x-cx)*(p.x-cx) + (p.y-cy)*(p.y-cy) + (p.z-cz)*(p.z-cz));
			p.x=cx+(p.x-cx)*distrat;
			p.y=cy+(p.y-cy)*distrat;
			p.z=cz+(p.z-cz)*distrat;
			
			this.pointList.add(p);
			return this.pointList.size()-1;
		}
		public Point get(int index)
		{
			return this.pointList.get(index);
		}
		public int getSize()
		{
			return this.pointList.size();
		}
	}
}
