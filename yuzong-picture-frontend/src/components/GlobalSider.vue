<template>
  <div id="globalSider">
    <a-layout-sider
      v-if="loginUserStore.loginUser.id"
      width="200"
      breakpoint="lg"
      collapsed-width="0"
    >
      <!-- 注意：这里去掉了 :selectedKeys="current" -->
      <a-menu mode="inline" :items="menuItems" @click="doMenuClick" />
    </a-layout-sider>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, ref, watchEffect } from 'vue'
import { PictureOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message } from 'ant-design-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()

// 固定的菜单列表
const fixedMenuItems = [
  {
    key: '/',
    icon: () => h(PictureOutlined),
    label: '公共图库',
  },
  {
    key: '/my_space',
    label: '我的空间',
    icon: () => h(UserOutlined),
  },
  {
    key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
    label: '创建团队',
    icon: () => h(TeamOutlined),
  },
]

const teamSpaceList = ref<API.SpaceUserVO[]>([])

// 高亮样式
const activeStyle = { backgroundColor: '#e6f7ff', color: '#1890ff', fontWeight: 'bold' }

const menuItems = computed(() => {
  // 注意：这里为了高亮稳定，我们依然使用 route.path 和 route.fullPath 进行匹配
  // 但因为 fullPath 现在会带时间戳，所以我们需要提取纯路径来比对
  const currentPath = route.path

  // 获取不带时间戳的原始 fullPath 用于匹配带参数的菜单（如创建团队）
  const currentFullPathWithoutTimestamp =
    route.fullPath.split('?')[0] + (route.query.type ? '?type=' + route.query.type : '')

  const applyActiveStyle = (item: any) => {
    // 匹配纯路径（如 /my_space）
    if (item.key === currentPath) {
      return { ...item, style: activeStyle }
    }
    // 匹配带参数的路径（如 /add_space?type=1）
    if (item.key.includes('?') && currentFullPathWithoutTimestamp === item.key) {
      return { ...item, style: activeStyle }
    }
    return item
  }

  const processedFixedMenus = fixedMenuItems.map(applyActiveStyle)

  if (teamSpaceList.value.length < 1) {
    return processedFixedMenus
  }

  const teamSpaceSubMenus = teamSpaceList.value.map((spaceUser) => {
    const space = spaceUser.space
    const key = '/space/' + spaceUser.spaceId
    return applyActiveStyle({
      key: key,
      label: space?.spaceName,
    })
  })

  const teamSpaceMenuGroup = {
    type: 'group',
    label: '我的团队',
    key: 'teamSpace',
    children: teamSpaceSubMenus,
  }

  return [...processedFixedMenus, teamSpaceMenuGroup]
})

const fetchTeamSpaceList = async () => {
  const res = await listMyTeamSpaceUsingPost()
  if (res.data.code === 0 && res.data.data) {
    teamSpaceList.value = res.data.data
  } else {
    message.error('加载我的团队空间失败，' + res.data.message)
  }
}

watchEffect(() => {
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

// ================= 核心修复部分 =================
// 路由跳转事件
const doMenuClick = ({ key }: { key: string }) => {
  // 解析 key 中的 path 和 query
  const [path, queryString] = key.split('?')
  const query: Record<string, string> = {}

  if (queryString) {
    queryString.split('&').forEach((param) => {
      const [k, v] = param.split('=')
      query[k] = v
    })
  }

  // 核心：给每次点击加上一个唯一的时间戳 _t
  // 这样即使路径相同，Vue Router 也会认为路由发生了变化（fullPath 变了）
  // 首页组件里的 watch(route) 就能捕获到变化，从而自动触发空搜索！
  query._t = Date.now().toString()

  router.push({
    path: path,
    query: query,
  })
}
//路由跳转事件
// const doMenuClick = ({ key }: { key: string }) => {
//   router.push(key)
//   }
// ================================================
</script>



<style scoped>
#globalSider .ant-layout-sider {
  background: none;
}
</style>

<!--<template>-->
<!--  <div id="globalSider">-->
<!--    <a-layout-sider-->
<!--      v-if="loginUserStore.loginUser.id"-->
<!--      width="200"-->
<!--      breakpoint="lg"-->
<!--      collapsed-width="0"-->
<!--    >-->
<!--      <a-menu-->
<!--        v-model:selectedKeys="current"-->
<!--        mode="inline"-->
<!--        :items="menuItems"-->
<!--        @click="doMenuClick"-->
<!--      />-->
<!--    </a-layout-sider>-->
<!--  </div>-->
<!--</template>-->

<!--<script lang="ts" setup>-->
<!--import { computed, h, ref, watchEffect } from 'vue'-->
<!--import { PictureOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'-->
<!--import { useRouter } from 'vue-router'-->
<!--import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'-->
<!--import { SPACE_TYPE_ENUM } from '@/constants/space.ts'-->
<!--import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'-->
<!--import { message } from 'ant-design-vue'-->

<!--const loginUserStore = useLoginUserStore()-->

<!--// 固定的菜单列表-->
<!--const fixedMenuItems = [-->
<!--  {-->
<!--    key: '/',-->
<!--    icon: () => h(PictureOutlined),-->
<!--    label: '公共图库',-->
<!--  },-->
<!--  {-->
<!--    key: '/my_space',-->
<!--    label: '我的空间',-->
<!--    icon: () => h(UserOutlined),-->
<!--  },-->
<!--  {-->
<!--    key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,-->
<!--    label: '创建团队',-->
<!--    icon: () => h(TeamOutlined),-->
<!--  },-->
<!--]-->

<!--const teamSpaceList = ref<API.SpaceUserVO[]>([])-->
<!--const menuItems = computed(() => {-->
<!--  // 如果用户没有团队空间，则只展示固定菜单-->
<!--  if (teamSpaceList.value.length < 1) {-->
<!--    return fixedMenuItems-->
<!--  }-->
<!--  // 如果用户有团队空间，则展示固定菜单和团队空间菜单-->
<!--  // 展示团队空间分组-->
<!--  const teamSpaceSubMenus = teamSpaceList.value.map((spaceUser) => {-->
<!--    const space = spaceUser.space-->
<!--    return {-->
<!--      key: '/space/' + spaceUser.spaceId,-->
<!--      label: space?.spaceName,-->
<!--    }-->
<!--  })-->
<!--  const teamSpaceMenuGroup = {-->
<!--    type: 'group',-->
<!--    label: '我的团队',-->
<!--    key: 'teamSpace',-->
<!--    children: teamSpaceSubMenus,-->
<!--  }-->
<!--  return [...fixedMenuItems, teamSpaceMenuGroup]-->
<!--})-->

<!--// 加载团队空间列表-->
<!--const fetchTeamSpaceList = async () => {-->
<!--  const res = await listMyTeamSpaceUsingPost()-->
<!--  if (res.data.code === 0 && res.data.data) {-->
<!--    teamSpaceList.value = res.data.data-->
<!--  } else {-->
<!--    message.error('加载我的团队空间失败，' + res.data.message)-->
<!--  }-->
<!--}-->

<!--/**-->
<!-- * 监听变量，改变时触发数据的重新加载-->
<!-- */-->
<!--watchEffect(() => {-->
<!--  // 登录才加载-->
<!--  if (loginUserStore.loginUser.id) {-->
<!--    fetchTeamSpaceList()-->
<!--  }-->
<!--})-->

<!--const router = useRouter()-->
<!--// 当前要高亮的菜单项-->
<!--const current = ref<string[]>([])-->
<!--// 监听路由变化，更新高亮菜单项-->
<!--router.afterEach((to, from, next) => {-->
<!--  current.value = [to.path]-->
<!--})-->

<!--// 路由跳转事件-->
<!--const doMenuClick = ({ key }) => {-->
<!--  router.push(key)-->
<!--}-->
<!--</script>-->

<!--<style scoped>-->
<!--#globalSider .ant-layout-sider {-->
<!--  background: none;-->
<!--}-->
<!--</style>-->
