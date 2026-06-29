<template>
  <div id="userLoginPage">
    <h2 class="title">智能云图库 - 用户登录</h2>
    <div class="desc">企业级智能协同云图库</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <!-- 【修改1】：添加 @keydown.enter.prevent 阻止默认提交，并触发跳转函数 -->
        <a-input
          v-model:value="formState.userAccount"
          placeholder="请输入账号"
          @keydown.enter.prevent="focusPassword"
        />
      </a-form-item>
      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码长度不能小于 8 位' },
        ]"
      >
        <!-- 【修改2】：添加 ref="passwordInputRef" 以便在脚本中获取该组件 -->
        <a-input-password
          ref="passwordInputRef"
          v-model:value="formState.userPassword"
          placeholder="请输入密码"
        />
      </a-form-item>
      <div class="tips">
        没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
// 【修改3】：从 vue 中引入 ref
import { reactive, ref } from 'vue'
import { userLoginUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

// 用于接受表单输入的值
const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const loginUserStore = useLoginUserStore()

// 【修改4】：定义密码输入框的 ref 变量
const passwordInputRef = ref()

// 【修改5】：定义回车跳转焦点的函数
const focusPassword = () => {
  // 调用密码输入框组件的 focus 方法
  passwordInputRef.value?.focus()
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userLoginUsingPost(values)
  // 登录成功，把登录态保存到全局状态中
  if (res.data.code === 0 && res.data.data) {
    await loginUserStore.fetchLoginUser()
    message.success('登录成功')
    router.push({
      path: '/',
      replace: true,
    })
  } else {
    message.error('登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userLoginPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  color: #bbb;
  text-align: right;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>

<!--<template>-->
<!--  <div id="userLoginPage">-->
<!--    <h2 class="title">智能云图库 - 用户登录</h2>-->
<!--    <div class="desc">企业级智能协同云图库</div>-->
<!--    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">-->
<!--      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">-->
<!--        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />-->
<!--      </a-form-item>-->
<!--      <a-form-item-->
<!--        name="userPassword"-->
<!--        :rules="[-->
<!--          { required: true, message: '请输入密码' },-->
<!--          { min: 8, message: '密码长度不能小于 8 位' },-->
<!--        ]"-->
<!--      >-->
<!--        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />-->
<!--      </a-form-item>-->
<!--      <div class="tips">-->
<!--        没有账号？-->
<!--        <RouterLink to="/user/register">去注册</RouterLink>-->
<!--      </div>-->
<!--      <a-form-item>-->
<!--        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>-->
<!--      </a-form-item>-->
<!--    </a-form>-->
<!--  </div>-->
<!--</template>-->
<!--<script lang="ts" setup>-->
<!--import { reactive } from 'vue'-->
<!--import { userLoginUsingPost } from '@/api/userController.ts'-->
<!--import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'-->
<!--import { message } from 'ant-design-vue'-->
<!--import router from '@/router' // 用于接受表单输入的值-->

<!--// 用于接受表单输入的值-->
<!--const formState = reactive<API.UserLoginRequest>({-->
<!--  userAccount: '',-->
<!--  userPassword: '',-->
<!--})-->

<!--const loginUserStore = useLoginUserStore()-->

<!--/**-->
<!-- * 提交表单-->
<!-- * @param values-->
<!-- */-->
<!--const handleSubmit = async (values: any) => {-->
<!--  const res = await userLoginUsingPost(values)-->
<!--  // 登录成功，把登录态保存到全局状态中-->
<!--  if (res.data.code === 0 && res.data.data) {-->
<!--    await loginUserStore.fetchLoginUser()-->
<!--    message.success('登录成功')-->
<!--    router.push({-->
<!--      path: '/',-->
<!--      replace: true,-->
<!--    })-->
<!--  } else {-->
<!--    message.error('登录失败，' + res.data.message)-->
<!--  }-->
<!--}-->
<!--</script>-->

<!--<style scoped>-->
<!--#userLoginPage {-->
<!--  max-width: 360px;-->
<!--  margin: 0 auto;-->
<!--}-->

<!--.title {-->
<!--  text-align: center;-->
<!--  margin-bottom: 16px;-->
<!--}-->

<!--.desc {-->
<!--  text-align: center;-->
<!--  color: #bbb;-->
<!--  margin-bottom: 16px;-->
<!--}-->

<!--.tips {-->
<!--  color: #bbb;-->
<!--  text-align: right;-->
<!--  font-size: 13px;-->
<!--  margin-bottom: 16px;-->
<!--}-->
<!--</style>-->
