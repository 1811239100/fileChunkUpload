package org.example.entity;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zyq
 * @description 上传分片文件参数
 * @date 2021/5/21 11:03
 **/
@Data
public class MultipartFileDTO {

    /**
     * 总分片数量
     **/
    private int chunks;

    /**
     * 当前为第几块分片
     **/
    private int chunk;

    /**
     * 当前分片大小
     **/
    private long size = 0L;

    /**
     * 文件名
     **/
    private String name;

    /**
     * 分片对象
     **/
    private MultipartFile file;

    /**
     * 文件生成的MD5
     **/
    private String md5;

}
