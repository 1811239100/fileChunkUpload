package org.example.service;



import org.example.entity.MultipartFileDTO;
import org.example.common.ChunkUploadResult;

import java.io.IOException;
public interface FileUploadService {
    ChunkUploadResult checkFileMd5ByRedis(String md5,String fileName) throws IOException;
    void uploadByRedis(MultipartFileDTO multipartFileDTO) throws IOException;
    ChunkUploadResult checkFileMd5ByDb(String md5,String fileName) throws IOException;
    void uploadByDb(MultipartFileDTO multipartFileDTO) throws IOException;
    ChunkUploadResult checkFileMd5(String md5,String fileName) throws IOException;
    void upload(MultipartFileDTO multipartFileDTO) throws IOException;
}
