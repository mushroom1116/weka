package Test;

import java.io.IOException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
/**
 * Created by rong on 2017/3/24.
 */
public class TestFile {

    public static void writeLog(String str)
    {
        try
        {
            String path="C:\\Users\\rong\\Desktop\\DataSet1\\log.txt";
            File file=new File(path);
            if(!file.exists())
                file.createNewFile();
            SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"+"\r\n");
            FileOutputStream out=new FileOutputStream(file,true); //如果追加方式用true
            StringBuffer sb=new StringBuffer();
            sb.append("-----------"+sdf.format(new Date())+"------------"+"\r\n");
            sb.append(str+"\r\n");
            out.write(sb.toString().getBytes("utf-8"));//注意需要转换对应的字符集
            out.close();
        }
        catch(IOException ex)
        {
            System.out.println(ex.getStackTrace());
        }
    }
}
