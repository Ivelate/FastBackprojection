package core;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import comparison.backprojection.Backprojection;
import file.CustomValuesTransientImage;
import geometry.Sphere;
import visualizer.TransientVisualizer;

/**
 * In order to use this program through matlab, one of this methods should be called.
 * Translates matlab data to our data, voxelizes and returns the resulting 3D confidence map.
 */
public class MatlabFacade 
{
	
	//Facade for REVEAL, without saving dumps to disk
	public static int[] performGpuBackprojectionReveal(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,double qualityWeight,String extraArgs[]) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,null,qualityWeight,null,null,extraArgs);
	}
	public static int[] performGpuBackprojectionReveal(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,double qualityWeight,String infoDumpFile,String extraArgs[]) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,null,qualityWeight,infoDumpFile,null,extraArgs);
	}
	
	
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,null);
	}
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,String dump3Dname) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,dump3Dname,1.0); // Default quality weight = 1
	}
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,String dump3Dname,double qualityWeight) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,dump3Dname,qualityWeight,null);
	}
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,String dump3Dname,double qualityWeight,String infoDumpFile) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,dump3Dname,qualityWeight,infoDumpFile,null);
	}
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,String dump3Dname,double qualityWeight,String infoDumpFile,String dump2Dname) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		return performGpuBackprojection(sI,laserpos,camptlist,ilaser_origin,icamera_origin,itpp,itimeshift,orthoM,worldSize,voxelSize,dump3Dname,qualityWeight,infoDumpFile,dump2Dname,null);
	}
	public static int[] performGpuBackprojection(double[][] sI,double[][]laserpos, double[][] camptlist, double[][] ilaser_origin, double[][] icamera_origin,double[][] itpp, double[][] itimeshift,double[][] orthoM,int[] worldSize,double voxelSize,String dump3Dname,double qualityWeight,String infoDumpFile,String dump2Dname,String[] extraArgs) throws IOException, LWJGLException, UnsupportedOperatingSystemException
	{
		//for(int i=0;i<extraArgs.length;i++) System.out.println("Extra args: "+extraArgs[i]);
		System.out.println("Starting Java part");
		System.setProperty("org.lwjgl.util.Debug", "true");
		TransientVoxelization.loadNatives();
		
		//camptlist to Vector3f array
		Vector3f[] customWallPoints=new Vector3f[camptlist[0].length];
		for(int i=0;i<camptlist[0].length;i++) customWallPoints[i]=new Vector3f((float)camptlist[0][i],(float)camptlist[1][i],(float)camptlist[2][i]);
		
		//laserpos to Vector3f array
		Vector3f[] lasers=new Vector3f[laserpos[0].length];
		for(int i=0;i<laserpos[0].length;i++) lasers[i]=new Vector3f((float)laserpos[0][i],(float)laserpos[1][i],(float)laserpos[2][i]);
		
		//laser and cam origins to Vector3f
		Vector3f laserOrigin=new Vector3f((float)ilaser_origin[0][0],(float)ilaser_origin[0][1],(float)ilaser_origin[0][2]);
		Vector3f camOrigin=new Vector3f((float)icamera_origin[0][0],(float)icamera_origin[0][1],(float)icamera_origin[0][2]);
		
		//Ortho matrix to Matrix4f
		FloatBuffer fb=BufferUtils.createFloatBuffer(16);
		for(int w=0;w<orthoM.length;w++) for(int h=0;h<orthoM[0].length;h++) fb.put((float)orthoM[w][h]);
		fb.flip();
		Matrix4f orthoMatrix=new Matrix4f();
		orthoMatrix.load(fb);
		
		//time per pixel, time shift
		double tpp=itpp[0][0];
		double timeshift=itimeshift[0][0];
		
		//The number of cam points give us the sI y coord
		int height=customWallPoints.length;
		int width=sI.length/height;
		int channels=1;
		
		//Wall dir
		Vector3f wallDir=Vector3f.sub(customWallPoints[1], customWallPoints[0], null).normalise(null);
		Vector3f lookTo=Vector3f.sub(customWallPoints[1], camOrigin, null).normalise(null);
		//Find normal using cross
		Vector3f wallNormal=Vector3f.cross(wallDir, lookTo, null); wallNormal.normalise(wallNormal); //Wall y right now.
		Vector3f.cross(wallDir, wallNormal, wallNormal); wallNormal.normalise(wallNormal);
		System.out.println("WALL NORMAL "+wallNormal);
		System.out.println(width+" "+height+" "+channels);
		//Loading into custom transient images
		CustomValuesTransientImage[] timgs=new CustomValuesTransientImage[sI[0].length];
		for(int i=0;i<timgs.length;i++)
		{
			float[][][] data=new float[width][height][channels];
			int cont=0;
			float min=Float.MAX_VALUE;
			float max=0;
			for(int y=0;y<height;y++)
			{
				for(int x=0;x<width;x++)
				{
					data[x][y][0]=(float)sI[cont][i];
					if(data[x][y][0]>0){
						if(data[x][y][0]<min) min=data[x][y][0];
						if(data[x][y][0]>max) max=data[x][y][0];
					}
					cont++;
				}
			}
			timgs[i]=new CustomValuesTransientImage(width,height,channels,(float)tpp,1,data,max,min,customWallPoints);
			//NEED THE WALL DIR AND NORMAL!
			timgs[i].setParamsForCamera(camOrigin, null, wallDir, wallNormal, 0, lasers[i], 0, 1,(float)timeshift,false);
			timgs[i].setLaserHitTime(Vector3f.sub(lasers[i], laserOrigin, null).length());
			//timgs[i].printToFile(new File("zz_transient_"+lasers[i].x+"_"+lasers[i].y+"_"+lasers[i].z+".png"));
		}
		
		System.out.println("Data parsed to java data structures. Beginning backprojection execution");
		TransientVoxelizationParams params=new TransientVoxelizationParams();
		if(extraArgs!=null) TransientVoxelization.parseArgsIntoParams(params, extraArgs,true); //Incomplete params == true, so no image storing by default will be set.
		//Most of those params will not be even needed, as the transient imgs are already initcialized
		params.USE_HALF_ELLIPSOIDS=false; //Custom wall points mean custom normals... which means its better to use the complete ellipsoids. FEEL FREE TO CHANGE THIS!! It reduces perfomance to a half, and its only useful on some situations.
		params.AUTO_CLEAN=false; params.FORCE_2D_BACKPROJECT=true; params.AUTO_MANAGE_DISPLAY=true;
		params.CUSTOM_TRANSIENT_IMAGES=timgs;
		params.camera=camOrigin; params.lookTo=lookTo; params.laserOrigin=laserOrigin;
		params.wallNormal=wallNormal; params.wallDir=wallDir;
		params.t_delta=(float)tpp; params.t0=(float)timeshift;
		params.lasers=lasers; params.orthoMatrix=orthoMatrix;
		params.voxelSize=(float)voxelSize;
		params.ERROR_THRESHOLD_WEIGHT=(float)qualityWeight;
		if(infoDumpFile!=null) params.executionInfoFile=new File(infoDumpFile);
		//debugh
		//params.setOrthoSize(800);
		//params.ORTHO_OFFSETX=186;
		//params.ORTHO_OFFSETY=39;
		//params.ORTHO_OFFSETZ=-400;
		
		params.VOXEL_RESOLUTION=worldSize[0];
		//params.saveImage=true;
		if(dump3Dname!=null&&!dump3Dname.isEmpty()){
			params.save3DDump=true;
			params.filename3d=new File(dump3Dname);
		}
		/*if(dump2Dname!=null&&!dump2Dname.isEmpty()){ //It doesnt work :(
			params.saveImage=true;
			params.filename2d=new File(dump2Dname);
		}*/
		
		//Let's do this
		TransientVoxelization tv=new TransientVoxelization(params);
		int[] data3d=tv.getCurrent3DData();
		tv.cleanup();

		return data3d;		
	}

	public static void generateDumpForRawData(int[] rawData,String dump3Dname,int xstart,int xend,int ystart,int yend,int zstart,int zend) throws IOException
	{
		int VOXEL_RESOLUTION=(int)Math.round(Math.cbrt(rawData.length));
		System.out.println("RES "+VOXEL_RESOLUTION);
		ArrayVoxelContainer container=new ArrayVoxelContainer(VOXEL_RESOLUTION,rawData);

		float maxIntensity=0;
		float[][][] filtered=new float[VOXEL_RESOLUTION][VOXEL_RESOLUTION][VOXEL_RESOLUTION];
		//LAPLACIAN FILTER
		for(int z=1;z<VOXEL_RESOLUTION-1;z++)
		{
			for(int y=1;y<VOXEL_RESOLUTION-1;y++)
			{
				for(int x=1;x<VOXEL_RESOLUTION-1;x++)
				{
					float c=container.get(x, y, z);
					float neighbours=0;
					for(int xf=-1;xf<=1;xf++) for(int yf=-1;yf<=1;yf++) for(int zf=-1;zf<=1;zf++) if(xf!=0||yf!=0||zf!=0) neighbours+=container.get(x+xf, y+yf, z+zf);		
					
					c=26*c - neighbours;
					
					if(z<zstart||z>=zend||x<xstart||x>=xend||y<ystart||y>=yend) c=0;
					
					if(c>maxIntensity) maxIntensity=c;
					filtered[x][y][z]=c;
				}
			}
		}
		//Normalize to Final Storage
		for(int z=0;z<VOXEL_RESOLUTION;z++)
		{
			for(int y=0;y<VOXEL_RESOLUTION;y++)
			{
				for(int x=0;x<VOXEL_RESOLUTION;x++)
				{
					float val=filtered[x][y][z]/(maxIntensity);
					if(val>1) val=1;
					filtered[x][y][z]=val;
				}
			}
		}
		
		TransientVoxelization.save3DBackprojectionToFile(filtered,new File(dump3Dname));
		TransientVoxelization.save2DBackprojectionToFile(filtered,new File(dump3Dname+"_heatmap.png"),false);
	}
}
