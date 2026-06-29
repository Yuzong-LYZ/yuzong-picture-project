<template>
  <!-- @author 程序员鱼皮 <a href="https://www.codefather.cn">编程导航原创项目</a> -->
  <div id="addPictureBatchPage">
    <h2 style="margin-bottom: 16px">批量创建</h2>
    <!-- 图片信息表单 -->
    <a-form name="formData" layout="vertical" :model="formData" @finish="handleSubmit">
      <a-form-item name="searchText" label="关键词">
        <a-input v-model:value="formData.searchText" placeholder="请输入关键词" allow-clear />
      </a-form-item>
      <a-form-item name="count" label="抓取数量">
        <a-input-number
          v-model:value="formData.count"
          placeholder="请输入数量"
          style="min-width: 180px"
          :min="1"
          :max="30"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="namePrefix" label="名称前缀">
        <a-input
          v-model:value="formData.namePrefix"
          placeholder="请输入名称前缀，会自动补充序号"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button
          type="primary"
          html-type="submit"
          style="width: 100%"
          :loading="loading"
          :disabled="loading"
        >
          执行任务
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
// import { message } from 'ant-design-vue'
// 原来：import { message } from 'ant-design-vue'  备注：优化成功
import { message, Modal } from 'ant-design-vue'
import { h } from 'vue'
import { CheckCircleOutlined } from '@ant-design/icons-vue' // 引入绿色对勾图标
import {
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
  uploadPictureByBatchUsingPost,
} from '@/api/pictureController.ts'
import { useRoute, useRouter } from 'vue-router'

const formData = reactive<API.PictureUploadByBatchRequest>({
  count: 10,
})
// 提交任务状态
const loading = ref(false)

const router = useRouter()

/**
 * 提交表单 优化成功
 * @param values
 */
const handleSubmit = async (values: any) => {
  loading.value = true
  try {
    const res = await uploadPictureByBatchUsingPost({ ...formData }, { timeout: 60000 })

    // 操作成功
    if (res.data.code === 0 && res.data.data) {
      Modal.confirm({
        title: '创建成功',
        content: `共成功创建 ${res.data.data} 条数据。`,
        // 【关键】使用 h 函数渲染绿色的对勾图标，替换掉默认的黄色感叹号
        icon: h(CheckCircleOutlined, { style: { color: '#52c41a' } }),
        okText: '返回首页',
        cancelText: '留在当前页',
        onOk() {
          router.push({ path: '/' })
        },
        onCancel() {
          // 留在当前页，关闭弹窗即可
        },
      })
    } else {
      message.error('创建失败，' + res.data.message)
    }
  } catch (error: any) {
    // 【优化3】针对“服务不给用”（通常是后端的 502/503/504 网关错误）做友好提示
    if (error.response && [502, 503, 504].includes(error.response.status)) {
      message.warning('服务正在努力启动中，请稍等几秒钟后再点击尝试~')
    } else {
      message.error('请求异常：' + (error.message || '网络错误或超时，请稍后重试'))
    }
    // message.error('请求异常：' + (error.message || '网络错误或超时，请稍后重试'))
  } finally {
    loading.value = false
  }
}
// const handleSubmit = async (values: any) => {
//   loading.value = true
//   try {
//     // 批量抓取非常耗时，通过 options 参数单独覆盖全局的 10s 超时，设置为 60s
//     const res = await uploadPictureByBatchUsingPost({ ...formData }, { timeout: 60000 })
//
//     // 操作成功
//     if (res.data.code === 0 && res.data.data) {
//       message.success(`创建成功，共 ${res.data.data} 条`)
//       // 跳转到主页
//       router.push({
//         path: `/`,
//       })
//     } else {
//       message.error('创建失败，' + res.data.message)
//     }
//   } catch (error: any) {
//     // 捕获网络断开、后端500或超时等异常，防止页面卡死且无提示
//     message.error('请求异常：' + (error.message || '网络错误或超时，请稍后重试'))
//   } finally {
//     // 【关键】无论成功还是失败，最终都必须关闭 loading
//     loading.value = false
//   }
// }

/**
 * 提交表单
 * @param values
 */
// const handleSubmit = async (values: any) => {
//   loading.value = true
//   const res = await uploadPictureByBatchUsingPost({
//     ...formData,
//   })
//   // 操作成功
//   if (res.data.code === 0 && res.data.data) {
//     message.success(`创建成功，共 ${res.data.data} 条`)
//     // 跳转到主页
//     router.push({
//       path: `/`,
//     })
//   } else {
//     message.error('创建失败，' + res.data.message)
//   }
//   loading.value = false
// }
</script>

<style scoped>
#addPictureBatchPage {
  max-width: 720px;
  margin: 0 auto;
}
</style>
