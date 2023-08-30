/*
 * @Author: 陈凯（实习） 1264504656@qq.com
 * @Date: 2023-08-21 14:38:57
 * @LastEditors: 陈凯（实习） 1264504656@qq.com
 * @LastEditTime: 2023-08-30 10:51:12
 * @FilePath: \MD5校验\js\script.js
 * @Description:
 *
 * Copyright (c) 2023 by ${git_name_email}, All Rights Reserved.
 */

$(document).ready(function () {
  var $ = jQuery,
    $list = $("#thelist")

  var flie_count = 0

  var nums = []

  var uploader

  //待上传文件的md5值（key为file id）
  var md5 = {}

  //打印md5值
  var log = (function () {
    var dom = $("#log")

    return function (str) {
      dom.append("<p>" + str + "</p>")
    }
  })()

  WebUploader.Uploader.register({
    // WebUploader提供的钩子（hook），在文件上传前先判断服务是否已存在这个文件
    "before-send-file": function (file) {
      //整个文件上传前
      //var that = this;
      var owner = this.owner
      var deferred = WebUploader.Deferred()

      console.log("beforeSendFile    [file MD5 value already calculated]")

      //上传前请求服务端,判断文件是否已经上传过

      $.ajax({
        url: "http://localhost:8081/fileUpload/checkFileMd5",
        type: "POST",
        data: { md5: md5[file.id], fileName: file.name },
        contentType: "application/x-www-form-urlencoded",
        async: true, // 是否异步
        dataType: "json",
        success: function (data) {
          console.log("beforeSend    data----->", data)
          data = typeof data == "string" ? JSON.parse(data) : data
          if (data.code === 200) {
            // 跳过当前文件并标记文件状态为上传完成
            owner.skipFile(file, WebUploader.File.Status.COMPLETE)
            nums[file.id] = []
            deferred.resolve()
          } else if (data.code === 202) {
            nums[file.id] = data.data
            deferred.resolve()
          } else {
            nums[file.id] = []
            deferred.resolve()
          }
        },
        error: function (xhr, status) {
          console.log("beforeSend    status----->", status)
        },
      })
      return deferred.promise()
    },
    //https://github.com/fex-team/webuploader/issues/139
    "before-send": function (block) {
      var file = block.file
      var me = this,
        owner = this.owner,
        deferred = $.Deferred()

      var blob = block.blob.getSource(),
        deferred = $.Deferred()

      // 这个肯定是异步的，需要通过FileReader读取blob后再算。
      // owner
      //   .md5File(block.blob)
      //   // 如果读取出错了，则通过reject告诉webuploader文件上传出错。
      //   .fail(function () {
      //     deferred.reject();
      //   })

      //   // md5值计算完成
      //   .then(function (md5) {
      // 方案二
      // 在这个文件上传前，一次性把所有已成功的分片md5拿到。
      // 在这里只需本地验证就ok
      if (
        nums[file.id].length != 0 &&
        !nums[file.id].includes(block.chunk)
      ) {
        deferred.reject()
      } else {
        deferred.resolve()
      }
      // });

      return deferred.promise()
    },
  })

  //创建webuploader
  uploader = WebUploader.create({
    // 设置自动上传
    auto: false,
    // swf文件路径
    swf: "./Uploader.swf",

    // 文件接收服务端。
    server: "http://localhost:8081/fileUpload/upload",

    // 选择文件的按钮。可选。
    // 内部根据当前运行是创建，可能是input元素，也可能是flash.
    pick: "#filePicker",

    // 开启分块上传
    chunked: true,
    chunkSize: 5 * 1024 * 1024, //5m分块
    chunkRetry: 3, //网络问题上传失败后重试次数

    fileSizeLimit: 10 * 1024 * 1024 * 1024, //最大10GB
    fileSingleSizeLimit: 5 * 1024 * 1024 * 1024, //单文件最大5GB

    // 不压缩image, 默认如果是jpeg，文件上传前会压缩一把再上传！
    resize: false,
  })

  // 当有文件添加进来的时候，打印出md5值
  uploader.on("fileQueued", function (file) {
    $list.append(
      '<div id="' +
      file.id +
      '" class="item">' +
      '<h4 class="info">' +
      file.name +
      '<button type="button" fileId="' +
      file.id +
      '" class="btn btn-danger btn-delete"><span class="glyphicon glyphicon-trash">删除项目</span></button></h4>' +
      '<p class="state">正在计算文件MD5...请等待计算完毕后再点击上传！</p>' +
      '<p class="md5"></p>' +
      '<p class="state"></p><input type="text" id="s_WU_FILE_' +
      flie_count +
      '" />' +
      "</div>"
    )

    flie_count++

    //删除要上传的文件
    //每次添加文件都给btn-delete绑定删除方法
    $(".btn-delete").click(function () {
      //console.log($(this).attr("fileId"));//拿到文件id
      uploader.removeFile(uploader.getFile($(this).attr("fileId"), true))
      $(this).parent().parent().fadeOut() //视觉上消失了
      $(this).parent().parent().remove() //DOM上删除了
    })
    uploader.options.formData.guid = WebUploader.guid() //每个文件都附带一个guid，以在服务端确定哪些文件块本来是一个文件的

    var start = +new Date()

    //获取文件MD5值
    md5[file.id] = ""

    // uploader
    //   .md5File(file)
    this.md5File(file, 0, 100 * 1024 * 1024)
      .progress(function (percentage) {
        // 及时显示进度
        $("#" + file.id)
          .find(".md5")
          .text("读取文件：" + parseInt(percentage * 100) + "%")
      })
      // 完成
      .then(function (fileMd5) {
        md5[file.id] = fileMd5

        var end = +new Date()
        log(
          "HTML5: md5 " +
          file.name +
          " cost " +
          (end - start) +
          "ms get value: " +
          fileMd5
        )

        // 完成
        file.wholeMd5 = fileMd5 //获取到了md5
        //uploader.options.formData.md5 = file.wholeMd5;//每个文件都附带一个md5，便于实现秒传

        $("#" + file.id)
          .find("p.state")
          .text("MD5计算完毕，可以点击上传了")
      })
  })

  //发送前填充数据
  uploader.on("uploadBeforeSend", function (block, data) {
    // block为分块数据
    console.log(block.chunk)
    // file为分块对应的file对象
    var file = block.file
    var fileMd5 = file.wholeMd5
    console.log(file)
    console.log(fileMd5)

    // 修改data可以控制发送哪些携带数据
    // 将存在file对象中的md5数据携带发送过去
    data.md5 = fileMd5 //md5

    //删除其他数据
    // if (block.chunks > 1) {
    //   //文件大于chunksize 分片上传
    //   data.isChunked = true;

    // } else {
    //   data.isChunked = false;
    // }

    // var deferred = WebUploader.Deferred();

    // //后端返回了已存在分片的数组，这里判断要发送的分片是否已存在
    // if (nums[file.id].length != 0 && !nums[file.id].includes(block.chunk)) {
    //   console.log("分片存在，已跳过:" + block.chunk);
    //   deferred.reject();
    // } else {
    //   deferred.resolve();
    // }
    // console.log("why");
    // return deferred.promise();
  })

  // 文件上传过程中创建进度条实时显示。
  uploader.on("uploadProgress", function (file, percentage) {
    var $li = $("#" + file.id),
      $percent = $li.find(".progress .progress-bar")

    // 避免重复创建
    if (!$percent.length) {
      $percent = $(
        '<div class="progress progress-striped active">' +
        '<div class="progress-bar" role="progressbar" style="width: 0%">' +
        "</div>" +
        "</div>"
      )
        .appendTo($li)
        .find(".progress-bar")
    }

    $li.find("p.state").text("上传中（" + parseInt(percentage * 100) + "%）")
  })

  //上传成功
  uploader.on("uploadSuccess", function (file) {
    $("#" + file.id)
      .find("p.state")
      .text("已上传")
    $("#" + file.id)
      .find(".progress")
      .find(".progress-bar")
      .attr("class", "progress-bar progress-bar-success")
    $("#" + file.id)
      .find(".info")
      .find(".btn")
      .fadeOut("slow") //上传完后删除"删除"按钮
    $("#StopBtn").fadeOut("slow")
  })
  uploader.on("uploadError", function (file) {
    $("#" + file.id)
      .find("p.state")
      .text("上传出错")
    //上传出错后进度条变红
    $("#" + file.id)
      .find(".progress")
      .find(".progress-bar")
      .attr("class", "progress-bar progress-bar-danger")
    //添加重试按钮
    //为了防止重复添加重试按钮，做一个判断
    //var retrybutton = $('#' + file.id).find(".btn-retry");
    //$('#' + file.id)
    if ($("#" + file.id).find(".btn-retry").length < 1) {
      var btn = $(
        '<button type="button" fileid="' +
        file.id +
        '" class="btn btn-success btn-retry"><span class="glyphicon glyphicon-refresh"></span></button>'
      )
      $("#" + file.id)
        .find(".info")
        .append(btn) //.find(".btn-danger")
    }
    $(".btn-retry").click(function () {
      //console.log($(this).attr("fileId"));//拿到文件id
      uploader.retry(uploader.getFile($(this).attr("fileId")))
    })
  })
  uploader.on("uploadAccept", function (file, response) {
    if (response._raw === '{"error":true}') {
      return false
    }
  })
  $("#UploadBtn").click(function () {
    uploader.upload() //上传
  })
  $("#StopBtn").click(function () {
    var status = $("#StopBtn").attr("status")
    if (status == "suspend") {
      console.log("当前按钮是暂停，即将变为继续")
      $("#StopBtn").html("继续上传")
      $("#StopBtn").attr("status", "continuous")
      console.log("当前所有文件===" + uploader.getFiles())
      console.log("=============暂停上传==============")
      uploader.stop(true)
      console.log("=============所有当前暂停的文件=============")
      console.log(uploader.getFiles("interrupt"))
    } else {
      console.log("当前按钮是继续，即将变为暂停")
      $("#StopBtn").html("暂停上传")
      $("#StopBtn").attr("status", "suspend")
      console.log("===============所有当前暂停的文件==============")
      console.log(uploader.getFiles("interrupt"))
      uploader.upload(uploader.getFiles("interrupt"))
    }
  })
})
