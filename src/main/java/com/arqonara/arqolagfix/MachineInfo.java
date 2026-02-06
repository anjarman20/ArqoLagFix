package com.arqonara.arqolagfix;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MachineInfo {
    private static OperatingSystemMXBean osBean;
    private static String cachedCpuModel = null;
    
    static {
        try {
            osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        } catch (Exception e) {
            osBean = null;
        }
    }
    
    public static String getOsName() {
        return System.getProperty("os.name", "Unknown");
    }
    
    public static String getOsVersion() {
        return System.getProperty("os.version", "Unknown");
    }
    
    public static String getOsArch() {
        return System.getProperty("os.arch", "Unknown");
    }
    
    public static int getCpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }
    
    public static String getCpuModel() {
        if (cachedCpuModel != null) {
            return cachedCpuModel;
        }
        
        // Try Linux /proc/cpuinfo first (VPS/Dedicated)
        try {
            File cpuinfo = new File("/proc/cpuinfo");
            if (cpuinfo.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(cpuinfo));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("model name") || line.startsWith("Model")) {
                        String[] parts = line.split(":");
                        if (parts.length > 1) {
                            cachedCpuModel = parts[1].trim();
                            reader.close();
                            return cachedCpuModel;
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            // Ignore, try other methods
        }
        
        // Try Windows (via wmic)
        try {
            Process process = Runtime.getRuntime().exec("wmic cpu get name");
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    cachedCpuModel = line.trim();
                    reader.close();
                    return cachedCpuModel;
                }
            }
            reader.close();
        } catch (Exception e) {
            // Ignore
        }
        
        // Fallback to system property
        String name = System.getProperty("os.arch", "Unknown Processor");
        cachedCpuModel = name + " (" + getCpuCores() + " cores)";
        return cachedCpuModel;
    }
    
    public static long getTotalMemory() {
        return Runtime.getRuntime().maxMemory();
    }
    
    public static long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }
    
    public static long getUsedMemory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }
    
    public static long getTotalDiskSpace() {
        File root = new File("/");
        return root.getTotalSpace();
    }
    
    public static long getFreeDiskSpace() {
        File root = new File("/");
        return root.getFreeSpace();
    }
    
    public static double getSystemCpuLoad() {
        if (osBean != null) {
            double load = osBean.getSystemCpuLoad();
            return load >= 0 ? load * 100.0 : 0.0;
        }
        return 0.0;
    }
    
    public static double getProcessCpuLoad() {
        if (osBean != null) {
            double load = osBean.getProcessCpuLoad();
            return load >= 0 ? load * 100.0 : 0.0;
        }
        return 0.0;
    }
    
    public static String getCpuModelShort() {
        String full = getCpuModel();
        // Shorten long names (e.g., "Intel(R) Xeon(R) CPU E5-2670 v3 @ 2.30GHz" -> "Xeon E5-2670 v3")
        if (full.length() > 40) {
            full = full.replaceAll("\\(R\\)|\\(TM\\)", "")
                       .replaceAll("\\s+", " ")
                       .trim();
            if (full.contains("@")) {
                full = full.substring(0, full.indexOf("@")).trim();
            }
        }
        return full;
    }
}
