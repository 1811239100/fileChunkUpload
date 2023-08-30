package org.example.common;

import lombok.Data;

@Data
public class ChunkUploadResult<T> {
    private T data;  //文件已上传时data为文件路径，部分上传时data为未上传文件块号
    private int tag; // 0:未上传 1:已上传 2:部分上传成功
    public ChunkUploadResult(T data,int tag){
        this.data=data;
        this.tag=tag;
    }

}
