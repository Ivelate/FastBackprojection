package core;
/**
 * helper class to check the operating system this Java VM runs in
 *
 * please keep the notes below as a pseudo-license
 *
 * http://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
 * compare to http://svn.terracotta.org/svn/tc/dso/tags/2.6.4/code/base/common/src/com/tc/util/runtime/Os.java
 * http://www.docjar.com/html/api/org/apache/commons/lang/SystemUtils.java.html
 */
import java.util.Locale;
public class OsCheck {
  /**
   * types of Operating Systems
   */
	public enum OperatingSystem
	{
		WINDOWS("windows"),
		LINUX("linux"),
		MACOSX("macosx");
		
		private final String soname;
		private OperatingSystem(String soname)
		{
			this.soname=soname;
		}
		
		public String getAsStringName()
		{
			return this.soname;
		}
	}

	//Cached SO (It will be the same in all executions of this method)
	private static OperatingSystem detectedOS = null;
	public static String fullDetectedOS=null;
	
  /**
   * detect the operating system from the os.name System property and cache
   * the result
   * 
   * @returns - the operating system detected
   */
  public static OperatingSystem getOperatingSystemType() throws UnsupportedOperatingSystemException
  {
    if (detectedOS == null) {
      String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
      fullDetectedOS=OS;
      if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
        detectedOS = OperatingSystem.MACOSX;
      } else if (OS.indexOf("win") >= 0) {
        detectedOS = OperatingSystem.WINDOWS;
      } else if (OS.indexOf("nux") >= 0) {
        detectedOS = OperatingSystem.LINUX;
      } else {
        throw new UnsupportedOperatingSystemException(OS);
      }
    }
    return detectedOS;
  }
}
