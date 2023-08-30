package org.example.service.impl;
import org.example.common.ChunkUploadResult;
import org.example.common.UploadConstants;
import org.example.entity.MultipartFileDTO;
import org.example.service.FileUploadService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FileUploadServiceImpl implements FileUploadService {
    @Value("${breakpoint.upload.chunkSize:null}")
    private long CHUNK_SIZE;
    @Value("${breakpoint.upload.dir:null}")
    private String finalDirPath; //文件上传路径
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public ChunkUploadResult checkFileMd5ByRedis(String md5,String fileName) throws IOException {
        Object processingObj = stringRedisTemplate.opsForValue().get(UploadConstants.status+md5);
        if (processingObj == null) {
            return new ChunkUploadResult(null,0);
        }
        boolean processing = Boolean.parseBoolean(processingObj.toString());
        String value = stringRedisTemplate.opsForValue().get(UploadConstants.fileKey + md5);
        //完整文件上传完成是true，未完成时false
        if (processing) {
            return new ChunkUploadResult(value,1);
        } else {
            File confFile = new File(value);
            byte[] completeList = FileUtils.readFileToByteArray(confFile);
            List<Integer> missChunkList = new LinkedList<>();
            for (int i = 0; i < completeList.length; i++) {
                if (completeList[i] != Byte.MAX_VALUE) {
                    //用空格补齐
                    missChunkList.add(i);
                }
            }
            return new ChunkUploadResult(missChunkList,2);
        }
    }

    @Override
    public void uploadByRedis(MultipartFileDTO multipartFileDTO) throws IOException {
        String fileName = multipartFileDTO.getName();
        String tempDirPath = finalDirPath + multipartFileDTO.getMd5();
        String tempFileName = fileName + "_tmp";
        File tmpDir = new File(tempDirPath);
        File tmpFile = new File(tempDirPath, tempFileName);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        RandomAccessFile accessTmpFile = new RandomAccessFile(tmpFile, "rw");
        long offset = CHUNK_SIZE * multipartFileDTO.getChunk();
        //定位到该分片的偏移量
        accessTmpFile.seek(offset);
        //写入该分片数据
        accessTmpFile.write(multipartFileDTO.getFile().getBytes());
        // 释放
        accessTmpFile.close();
        boolean isOk = checkAndSetUploadProgressByRedis(multipartFileDTO, tempDirPath);
        if (isOk) {
            boolean flag = renameFile(tmpFile, fileName);
            System.out.println("upload complete !!" + flag + " name=" + fileName);
        }
    }
    @Transactional
    @Override
    public ChunkUploadResult checkFileMd5ByDb(String md5,String fileName) throws IOException {
        String isTableSql = String.format("SELECT COUNT(*) as count FROM information_schema.TABLES WHERE " + "table_name = '%s'", "chunkFile");
        Map<String, Object> tableMap= jdbcTemplate.queryForMap(isTableSql);
        String createTableSql="create table chunkFile " +
                "(id varchar(255),status varchar(255),filepath varchar(255))";
        if(Integer.parseInt(tableMap.get("count").toString()) == 0){
            jdbcTemplate.execute(createTableSql);
        }
        String sql="SELECT * FROM chunkFile where id = ?";
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql, md5);
        if(maps.size()==0) {
            jdbcTemplate.update("insert into chunkFile (id,status,filepath) values (?,?,?)",md5,"false","");
            return new ChunkUploadResult(null, 0);
        }
        Map<String, Object>map=maps.get(0);
        Boolean status=Boolean.parseBoolean(((String)map.get("status")));
        String value=(String)map.get("filepath");
        if(status)return new ChunkUploadResult(value,1);
        else {
            File confFile = new File(value);
            byte[] completeList = FileUtils.readFileToByteArray(confFile);
            List<Integer> missChunkList = new LinkedList<>();
            for (int i = 0; i < completeList.length; i++) {
                if (completeList[i] != Byte.MAX_VALUE) {
                    //用空格补齐
                    missChunkList.add(i);
                }
            }
            return new ChunkUploadResult(missChunkList,2);
        }
    }
    @Transactional
    @Override
    public void uploadByDb(MultipartFileDTO multipartFileDTO) throws IOException {
        String fileName = multipartFileDTO.getName();
        String tempDirPath = finalDirPath + multipartFileDTO.getMd5();
        String tempFileName = fileName + "_tmp";
        File tmpDir = new File(tempDirPath);
        File tmpFile = new File(tempDirPath, tempFileName);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        RandomAccessFile accessTmpFile = new RandomAccessFile(tmpFile, "rw");
        long offset = CHUNK_SIZE * multipartFileDTO.getChunk();
        //定位到该分片的偏移量
        accessTmpFile.seek(offset);
        //写入该分片数据
        accessTmpFile.write(multipartFileDTO.getFile().getBytes());
        // 释放
        accessTmpFile.close();
        boolean isOk = checkAndSetUploadProgressByDb(multipartFileDTO, tempDirPath);
        if (isOk) {
            boolean flag = renameFile(tmpFile, fileName);
            System.out.println("upload complete !!" + flag + " name=" + fileName);
        }

    }

    @Override
    public ChunkUploadResult checkFileMd5(String md5,String fileName) throws IOException {
        File file = new File(finalDirPath+md5);
        if(!file.exists()){
            return new ChunkUploadResult(null,0);
        }
        file=new File(finalDirPath+md5+"/"+fileName);
        if(file.exists()){
            return new ChunkUploadResult(file.getAbsolutePath(),1);
        }
        file=new File(finalDirPath+md5+"/"+fileName+".conf");
        if(file.exists()){
            byte[] completeList = FileUtils.readFileToByteArray(file);
            List<Integer> missChunkList = new LinkedList<>();
            for (int i = 0; i < completeList.length; i++) {
                if (completeList[i] != Byte.MAX_VALUE) {
                    //用空格补齐
                    missChunkList.add(i);
                }
            }
            return new ChunkUploadResult(missChunkList,2);
        }
        return new ChunkUploadResult(null,0);
    }

    @Override
    public void upload(MultipartFileDTO multipartFileDTO) throws IOException {
        String fileName = multipartFileDTO.getName();
        String tempDirPath = finalDirPath + multipartFileDTO.getMd5();
        String tempFileName = fileName + "_tmp";
        File tmpDir = new File(tempDirPath);
        File tmpFile = new File(tempDirPath, tempFileName);
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        RandomAccessFile accessTmpFile = new RandomAccessFile(tmpFile, "rw");
        long offset = CHUNK_SIZE * multipartFileDTO.getChunk();
        //定位到该分片的偏移量
        accessTmpFile.seek(offset);
        //写入该分片数据
        accessTmpFile.write(multipartFileDTO.getFile().getBytes());
        // 释放
        accessTmpFile.close();
        boolean isOk = checkAndSetUploadProgress(multipartFileDTO, tempDirPath);
        if (isOk) {
            boolean flag = renameFile(tmpFile, fileName);
            System.out.println("upload complete !!" + flag + " name=" + fileName);
        }
    }

    private boolean checkAndSetUploadProgressByDb(MultipartFileDTO multipartFileDTO, String uploadDirPath) throws IOException {
        String fileName = multipartFileDTO.getName();
        //路径/filename.conf
        File confFile = new File(uploadDirPath, fileName + ".conf");
        RandomAccessFile accessConfFile = new RandomAccessFile(confFile, "rw");
        //把该分段标记为 true 表示完成
        System.out.println("set part " + multipartFileDTO.getChunk() + " complete");
        accessConfFile.setLength(multipartFileDTO.getChunks());
        accessConfFile.seek(multipartFileDTO.getChunk());
        accessConfFile.write(Byte.MAX_VALUE);
        //completeList 检查是否全部完成,如果数组里是否全部都是(全部分片都成功上传)
        byte[] completeList = FileUtils.readFileToByteArray(confFile);
        byte isComplete = Byte.MAX_VALUE;
        for (int i = 0; i < completeList.length && isComplete == Byte.MAX_VALUE; i++) {
            //与运算, 如果有部分没有完成则 isComplete 不是 Byte.MAX_VALUE
            isComplete = (byte) (isComplete & completeList[i]);
        }
        accessConfFile.close();
        //更新redis中的状态：如果是true的话证明是已经该大文件全部上传完成
        if (isComplete == Byte.MAX_VALUE) {
            jdbcTemplate.update("update chunkFile set status = ?,filepath = ? where id = ?","true",uploadDirPath + "/" + fileName,multipartFileDTO.getMd5());
            return true;
        } else {
            jdbcTemplate.update("update chunkFile set status = ?,filepath = ? where id = ?","false",uploadDirPath + "/" + fileName,multipartFileDTO.getMd5()+".conf");
            return false;
        }
    }

    private boolean checkAndSetUploadProgressByRedis(MultipartFileDTO multipartFileDTO, String uploadDirPath) throws IOException {
        String fileName = multipartFileDTO.getName();
        //路径/filename.conf
        File confFile = new File(uploadDirPath, fileName + ".conf");
        RandomAccessFile accessConfFile = new RandomAccessFile(confFile, "rw");
        //把该分段标记为 true 表示完成
        System.out.println("set part " + multipartFileDTO.getChunk() + " complete");
        accessConfFile.setLength(multipartFileDTO.getChunks());
        accessConfFile.seek(multipartFileDTO.getChunk());
        accessConfFile.write(Byte.MAX_VALUE);
        //completeList 检查是否全部完成,如果数组里是否全部都是(全部分片都成功上传)
        byte[] completeList = FileUtils.readFileToByteArray(confFile);
        byte isComplete = Byte.MAX_VALUE;
        for (int i = 0; i < completeList.length && isComplete == Byte.MAX_VALUE; i++) {
            //与运算, 如果有部分没有完成则 isComplete 不是 Byte.MAX_VALUE
            isComplete = (byte) (isComplete & completeList[i]);
            System.out.println("check part " + i + " complete?:" + completeList[i]);
        }

        accessConfFile.close();
        //更新redis中的状态：如果是true的话证明是已经该大文件全部上传完成
        if (isComplete == Byte.MAX_VALUE) {
            stringRedisTemplate.opsForValue().set(UploadConstants.status+multipartFileDTO.getMd5(), "true");
            stringRedisTemplate.opsForValue().set(UploadConstants.fileKey + multipartFileDTO.getMd5(), uploadDirPath + "/" + fileName);
            return true;
        } else {
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(UploadConstants.status + multipartFileDTO.getMd5()))) {
                stringRedisTemplate.opsForValue().set(UploadConstants.status+multipartFileDTO.getMd5(), "false");
            }
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(UploadConstants.fileKey + multipartFileDTO.getMd5()))) {
                stringRedisTemplate.opsForValue().set(UploadConstants.fileKey + multipartFileDTO.getMd5(), uploadDirPath + "/" + fileName + ".conf");
            }
            return false;
        }
    }
    private boolean checkAndSetUploadProgress(MultipartFileDTO multipartFileDTO, String uploadDirPath) throws IOException {
        String fileName = multipartFileDTO.getName();
        //路径/filename.conf
        File confFile = new File(uploadDirPath, fileName + ".conf");
        RandomAccessFile accessConfFile = new RandomAccessFile(confFile, "rw");
        //把该分段标记为 true 表示完成
        System.out.println("set part " + multipartFileDTO.getChunk() + " complete");
        accessConfFile.setLength(multipartFileDTO.getChunks());
        accessConfFile.seek(multipartFileDTO.getChunk());
        accessConfFile.write(Byte.MAX_VALUE);
        //completeList 检查是否全部完成,如果数组里是否全部都是(全部分片都成功上传)
        byte[] completeList = FileUtils.readFileToByteArray(confFile);
        byte isComplete = Byte.MAX_VALUE;
        for (int i = 0; i < completeList.length && isComplete == Byte.MAX_VALUE; i++) {
            //与运算, 如果有部分没有完成则 isComplete 不是 Byte.MAX_VALUE
            isComplete = (byte) (isComplete & completeList[i]);
        }
        accessConfFile.close();
        //更新redis中的状态：如果是true的话证明是已经该大文件全部上传完成
        if (isComplete == Byte.MAX_VALUE) {
            return true;
        } else {
            return false;
        }
    }
    /**
     * 文件重命名
     *
     * @multipartFileDTO toBeRenamed   将要修改名字的文件
     * @multipartFileDTO toFileNewName 新的名字
     * @return
     */
    public boolean renameFile(File toBeRenamed, String toFileNewName) {
        //检查要重命名的文件是否存在，是否是文件
        if (!toBeRenamed.exists() || toBeRenamed.isDirectory()) {
            return false;
        }
        String p = toBeRenamed.getParent();
        File newFile = new File(p + File.separatorChar + toFileNewName);
        //修改文件名
        return toBeRenamed.renameTo(newFile);
    }
}
