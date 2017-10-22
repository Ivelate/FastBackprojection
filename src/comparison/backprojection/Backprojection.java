package comparison.backprojection;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.DoubleAdder;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Vector3f;

import core.BoxBounds;
import core.FilterResults;
import core.TransientVoxelization;
import core.TransientVoxelizationParams;
import file.AcceptedFileName;
import file.TransientImage;

public class Backprojection 
{
	//This class needs a lot of memory. Is mandatory to extend the JVM memory if needed
	public Backprojection(TransientVoxelizationParams params,int numThreads,int transientImageChunkSize,boolean INVERSE,String readroute) throws IOException
	{
		/*File f=new File("result_backprojection_001_dump.dump");
		double[][][] backprojected=this.load3DBackprojectionFromFile(f);*/
		
		int resolution=params.VOXEL_RESOLUTION; //For the sake of writing less
		BoxBounds bounds=new BoxBounds(params.ORTHO_OFFSETX,params.ORTHO_OFFSETY,params.ORTHO_OFFSETZ,params.getMaxOrthoSize(),params.VOXEL_RESOLUTION);
		
		//Result box, with lots of precision and those things
		BackprojectionValueStorer backprojected=null;
		
		long finalTime=0;
		if(readroute==null){
			boolean end=false;
			int imageCount=0;
			backprojected=(INVERSE&&numThreads>1)?new BackprojectionConcurrentValueStorer(resolution):new BackprojectionNonConcurrentValueStorer(resolution);
			do
			{
				TransientImage[] transientImages=params.CUSTOM_TRANSIENT_IMAGES==null?
						initTransientImages(params.inputFolder,params.acceptedFileName,params.lasers,params.fov,params.streakYratio,params.t_delta,params.t0,params.camera,params.lookTo,params.laserOrigin,params.UNWARP_CAMERA,params.UNWARP_LASER,params.OVERRIDE_TRANSIENT_WALL_POINTS,imageCount,transientImageChunkSize) :
							params.CUSTOM_TRANSIENT_IMAGES;
				if(params.CUSTOM_TRANSIENT_IMAGES!=null) transientImageChunkSize=transientImages.length; //All transient images are loaded
				
				if(transientImages.length>0)
				{
					imageCount+=transientImages.length;
					long itime=System.currentTimeMillis();
					System.out.println();
					System.out.println("Starting "+(INVERSE?"Inverse ":"")+"Backprojection for folder "+params.inputFolder.getAbsolutePath());
								
					Semaphore lock=new Semaphore(-numThreads+1);
					BackprojectionMonitor monitor=new BackprojectionMonitor(params.VOXEL_RESOLUTION-1);
					for(int t=0;t<numThreads;t++){
						System.out.println("T"+t);
						Thread bthread=INVERSE?	new InverseBackprojectionThread(transientImages,bounds,numThreads,t,backprojected,lock):
												new BackprojectionThread(transientImages,bounds,numThreads,t,backprojected,lock,monitor);
						bthread.start();
					}
					
					//Blocks until all threads completed their jobs
					try {
						lock.acquire();
					} catch (InterruptedException e) {
						System.err.println("Wait interrupted. The result is not guaranteed to be correct");
					}
		
					finalTime+=System.currentTimeMillis()-itime;
					System.out.println("Done! Time elapsed: "+((float)finalTime)/1000+"s");
				}
				else end=true;
				if(transientImages.length<transientImageChunkSize) end=true;
				
			}while(!end);
		}
		else {
			backprojected=new BackprojectionNonConcurrentValueStorer(this.load3DBackprojectionFromFile(new File(readroute)));
			resolution=backprojected.getResolution();
		}

		//Done! Lets print an image or something
		File outImageFile;
		int res=0;
		do
		{
			String nres=""+res;
			while(nres.length()<3)nres="0"+nres;
			outImageFile=new File("result_backprojection_"+nres+".png");
			res++;
		} while(outImageFile.exists());
		
		if(params.save3DRaw) save3DBackprojectionToFile(backprojected,params.filename3draw!=null?params.filename3draw:new File(outImageFile.getAbsolutePath()+"_raw.dump"));
		FilterResults filterResults=applyFilters(resolution,backprojected);
		//this.saveAsDotCloud(backprojected, new File("SPAR_raw_dotcloud.dcloud"), 0.5f);
		
		try{
			if(params.saveImage) saveFinalTextureToFile(filterResults.maxResult,filterResults.maxResultDepth,filterResults.maxIntensity,params.filename2d!=null?params.filename2d:outImageFile,params.printGrayscale);
		}
		catch(IOException e){
			System.err.println("Problem storing result image files");e.printStackTrace();
		}
		try{
			if(params.save3DDump) save3DBackprojectionToFile(backprojected,params.filename3d!=null?params.filename3d:new File(outImageFile.getParentFile(),outImageFile.getName().replaceFirst("[.][^.]+$", "")+"_dump.dump"));
		}
		catch(IOException e){
			System.err.println("Problem storing full backprojection dump file");e.printStackTrace();
		}
		try{
			if(params.executionInfoFile!=null)
			{
				PrintStream iout=new PrintStream(new FileOutputStream(params.executionInfoFile));
				iout.println("Voxel dimensions  x: "+params.VOXEL_RESOLUTION);
				iout.println("                  y: "+params.VOXEL_RESOLUTION);
				iout.println("                  z: "+params.VOXEL_RESOLUTION);
				iout.println();
				iout.println("Execution time: "+finalTime+"ms");
				iout.close();
			}
		}
		catch(IOException e){
			System.err.println("Problem storing txt info file");e.printStackTrace();
		}
	}
	
	public FilterResults applyFilters(int resolution,BackprojectionValueStorer backprojected)
	{
		double[][] maxResult=new double[resolution][resolution];
		int[][] maxResultDepth=new int[resolution][resolution];
		double maxIntensity=0;
		//DOUBLE DERIVATIVE Z FILTER
		double[][][] filtered=new double[resolution][resolution][resolution];
		//LAPLACIAN FILTER
		for(int x=1;x<resolution-1;x++)
		{
			for(int y=1;y<resolution-1;y++)
			{
				for(int z=1;z<resolution-1;z++)
				{
					double c=backprojected.get(x, y, z);
					double neighbours=0;
					for(int xf=-1;xf<=1;xf++) for(int yf=-1;yf<=1;yf++) for(int zf=-1;zf<=1;zf++) if(xf!=0||yf!=0||zf!=0) neighbours+=backprojected.get(x+xf, y+yf, z+zf);
					
					c=26*c - neighbours;
					if(c>maxIntensity) maxIntensity=c;
					filtered[x][y][z]=c;
				}
			}
		}
		//Normalize to Final Storage
		for(int x=0;x<resolution;x++)
		{
			for(int y=0;y<resolution;y++)
			{
				for(int z=0;z<resolution;z++)
				{
					double val=filtered[x][y][z]/(maxIntensity);
					if(val>1) val=1;
					backprojected.set(x, y, z, val);
				}
			}
		}
		for(int x=0;x<resolution;x++)
		{
			for(int y=0;y<resolution;y++)
			{
				for(int z=0;z<resolution;z++)
				{
					//if(maxResult[x][y]<filtered[x][y][z]) maxResult[x][y]=filtered[x][y][z];
					if(maxResult[x][y]<backprojected.get(x, y, z)) {
						maxResult[x][y]=backprojected.get(x, y, z);
						maxResultDepth[x][y]=z;
					}
				}
			}
		}
		
		return new FilterResults(maxResult,maxResultDepth,1);
	}
	
	private TransientImage[] initTransientImages(String route,AcceptedFileName afn,Vector3f[] lasers,float fov,float streakyratio,float timeScale,float t0,Vector3f camera,Vector3f lookTo,Vector3f laserOrigin,boolean unwarpCamera,boolean unwarpLaser,Vector3f[] customWallPoints) throws IOException 
	{
		return this.initTransientImages(new File(route), afn, lasers, fov, streakyratio, timeScale, t0, camera, lookTo, laserOrigin,unwarpCamera,unwarpLaser,customWallPoints);
	}
	private TransientImage[] initTransientImages(File folder,AcceptedFileName afn,Vector3f[] lasers,float fov,float streakyratio,float timeScale,float t0,Vector3f camera,Vector3f lookTo,Vector3f laserOrigin,boolean unwarpCamera,boolean unwarpLaser,Vector3f[] customWallPoints) throws IOException 
	{
		return initTransientImages(folder,afn,lasers,fov,streakyratio,timeScale,t0,camera,lookTo,laserOrigin,unwarpCamera,unwarpLaser,customWallPoints,0,Integer.MAX_VALUE);
	}
	private TransientImage[] initTransientImages(File folder,AcceptedFileName afn,Vector3f[] lasers,float fov,float streakyratio,float timeScale,float t0,Vector3f camera,Vector3f lookTo,Vector3f laserOrigin,boolean unwarpCamera,boolean unwarpLaser,Vector3f[] customWallPoints,int from,int toSize) throws IOException 
	{
		System.out.println("Loading Streak Images from disk");
		//Assumng constant normal, dir |TODO
		Vector3f wallDir=new Vector3f(1,0,0);
		Vector3f wallNormal=new Vector3f(0,0,1);
		long ti=System.currentTimeMillis();
		LinkedList<File> files=new LinkedList<File>();
		int untilAccept=from;
		int untilReject=toSize+from;
		for(File f:folder.listFiles())
		{
			if(afn.isAccepted(f)) {
				if(untilAccept<=0&&untilReject>0) files.add(f);
				untilAccept--;
				untilReject--;
				if(untilReject<=0) break;
			}
		}
		
		TransientImage[] transientImages=new TransientImage[files.size()];
		int cont=0;
		for(File f:files)
		{
			transientImages[cont]=TransientVoxelization.initTransientImage(f, timeScale,t0,-1, lasers,fov,streakyratio,camera,lookTo,wallDir,wallNormal,laserOrigin,unwarpCamera,unwarpLaser,afn,customWallPoints);
			cont++;
			System.out.println(cont+"/"+transientImages.length+" loaded");
		}
		
		System.out.println("Done loading images, time "+((float)(System.currentTimeMillis()-ti)/1000)+"s");
		return transientImages;
	}
	
	private void saveAsDotCloud(BackprojectionValueStorer backprojection,File f,float threshold) throws IOException
	{
		DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
		
		out.writeInt(backprojection.getResolution()); //Writing length as first data
		
		for(int x=0;x<backprojection.getResolution();x++)
		{
			for(int y=0;y<backprojection.getResolution();y++)
			{
				for(int z=0;z<backprojection.getResolution();z++)
				{
					out.writeBoolean(backprojection.get(x, y, z)>threshold);
				}
			}
		}
		
		out.close();
	}
	
	private void save3DBackprojectionToFile(BackprojectionValueStorer backprojection,File f) throws IOException
	{
		DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
		
		out.writeInt(backprojection.getResolution()); //Writing length as first data
		
		for(int x=0;x<backprojection.getResolution();x++)
		{
			for(int y=0;y<backprojection.getResolution();y++)
			{
				for(int z=0;z<backprojection.getResolution();z++)
				{
					out.writeDouble(backprojection.get(x, y, z));
				}
			}
		}
		
		out.close();
	}
	
	public double[][][] load3DBackprojectionFromFile(File f) throws IOException
	{
		DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
		
		int resolution=in.readInt();
		boolean readAsDouble=true;
		if(resolution < 0){
			resolution=-resolution;
			readAsDouble=false;
		}
		double[][][] backprojection=new double[resolution][resolution][resolution];
		
		for(int x=0;x<backprojection[0][0].length;x++)
		{
			for(int y=0;y<backprojection[0].length;y++)
			{
				for(int z=0;z<backprojection.length;z++)
				{
					backprojection[x][y][z]=readAsDouble?in.readDouble():in.readFloat();
				}
			}
		}
		
		in.close();
		return backprojection;
	}
	
	private void saveFinalTextureToFile(double[][] maxIntensities,int[][] maxIntensitiesDepth,double maxIntensity,File file,boolean printGrayScale) throws IOException
	{		
		BufferedImage off_Image =
				  new BufferedImage(maxIntensities.length, maxIntensities[0].length,
				                    BufferedImage.TYPE_INT_RGB);
		BufferedImage depth_Image =
				  new BufferedImage(maxIntensitiesDepth.length, maxIntensitiesDepth[0].length,
				                    BufferedImage.TYPE_INT_RGB);

		if(maxIntensity>0){
			for(int x=0;x<maxIntensities.length;x++) for(int y=0;y<maxIntensities[0].length;y++) {
				
				int c=(int)((maxIntensities[x][y]*255) / maxIntensity);
				int d=maxIntensitiesDepth[x][y]*255 / maxIntensities.length;
				
				off_Image.setRGB(x, y, TransientVoxelization.getRgbFor(c,printGrayScale));
				depth_Image.setRGB(x, y, c>0?TransientVoxelization.getRgbFor(d,printGrayScale):0);
			}
		}
	
		ImageIO.write(off_Image, "PNG", file);
		ImageIO.write(depth_Image, "PNG", new File(file.getParentFile(),file.getName().replaceFirst("[.][^.]+$", "")+"_depth.png"));
	}
	

	/************************* MAIN  ************************************/
	public static void main(String[] args) throws IOException
	{		
		TransientVoxelizationParams params=new TransientVoxelizationParams();
		TransientVoxelization.parseArgsIntoParams(params, args);
		int numThreads=2;
		int transientImageChunkSize=Integer.MAX_VALUE;
		
		String route=args.length>0&&args[0].equals("-parseRoute")?args[1]:null;
		for(int i=0;i<args.length;i++)
		{
			switch(args[i])
			{
			case "-threads":
				numThreads=Integer.parseInt(args[++i]);
				break;
			case "-transientImageChunkSize":
				transientImageChunkSize=Integer.parseInt(args[++i]);
				break;
			}
		}
		new Backprojection(params,numThreads,transientImageChunkSize,false,route);
		//new Backprojection(route,new AcceptedFileName(2,2,".float"),lasers,(float)Math.toRadians(90),tp,bounds,2,true/*,"SPAD_raw_backprojection.dump"*/);
	}
}
