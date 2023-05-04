### 我照着 [别人的代码](https://github.com/darylgo/Camera2Sample)，学习Camera2 的能力和用法
### 参考
* [Android Camera API2 介绍](https://redspider110.github.io/2018/11/28/0109-android-camera-3-api2/)
----
### 个人的理解
1. camera2 是比较复杂的
2. 各个功能拆的比较散，组合在一起，功能又比较强大，有点组件化的意思
3. 涉及到的主要类
   1. CameraDevice   ： 代表当前相机设备
   2. ImageReader    ： 相机预览时，可获取的yuv 数据
   3. CaptureSession : 拍摄会话
   4. CaptureRequest : 拍摄请求，包含CameraDevice 的参数设置，算法控制，输出缓冲区等
   5. CaptureResult  : 拍摄结果
   6. surface        : 数据缓冲的句柄，从ImageReader的getSurface() 函数获取
   7. CameraCharacteristics : 相机属性
   8. TextureView : 显示预览的图像
   