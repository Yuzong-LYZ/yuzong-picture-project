<template>
  <div id="homePage" ref="homePageRef">
    <!-- 搜索框 -->
    <div class="search-bar">
      <a-input-search
        v-model:value="searchParams.searchText"
        placeholder="从海量图片中搜索"
        enter-button="搜索"
        size="large"
        @search="doSearch"
      />
    </div>
    <!-- 分类和标签筛选 -->
    <a-tabs v-model:active-key="selectedCategory" @change="doSearch">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />
    </a-tabs>
    <div class="tag-bar">
      <span style="margin-right: 8px">标签：</span>
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
          v-for="(tag, index) in tagList"
          :key="tag"
          v-model:checked="selectedTagList[index]"
          @change="doSearch"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
    </div>
    <!-- 图片列表 -->
    <PictureList :dataList="dataList" :loading="loading" />

    <!-- 加载更多提示 -->
    <div v-if="isLoadingMore" style="text-align: center; padding: 16px">
      <a-spin tip="加载更多图片中..." />
    </div>
    <!-- 没有更多数据提示 -->
    <div
      v-if="noMoreData && dataList.length > 0"
      style="text-align: center; padding: 16px; color: #999"
    >
      —— 已经到底啦 ——
    </div>

    <!-- 滚动触发的哨兵元素（不可见） -->
    <div ref="sentinelRef" style="height: 1px"></div>

    <!-- 分页 -->
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />
  </div>
</template>

<!--<script setup lang="ts">-->
<!--import { onMounted, onUnmounted, reactive, ref, nextTick } from 'vue'-->
<!--import {-->
<!--  listPictureTagCategoryUsingGet,-->
<!--  listPictureVoByPageUsingPost,-->
<!--} from '@/api/pictureController.ts'-->
<!--import { message } from 'ant-design-vue'-->
<!--import PictureList from '@/components/PictureList.vue'-->

<!--// ===================== 数据定义 =====================-->
<!--const dataList = ref<API.PictureVO[]>([])-->
<!--const total = ref(0)-->
<!--const loading = ref(true)-->

<!--// 新增：加载更多相关状态-->
<!--const isLoadingMore = ref(false) // 是否正在加载更多-->
<!--const noMoreData = ref(false) // 是否已加载全部数据-->

<!--// 搜索条件-->
<!--const searchParams = reactive<API.PictureQueryRequest>({-->
<!--  current: 1,-->
<!--  pageSize: 12, // 每页条数保持不变-->
<!--  sortField: 'createTime',-->
<!--  sortOrder: 'descend',-->
<!--})-->

<!--// 哨兵元素引用（用于 IntersectionObserver）-->
<!--const sentinelRef = ref<HTMLElement | null>(null)-->
<!--const homePageRef = ref<HTMLElement | null>(null)-->
<!--let observer: IntersectionObserver | null = null-->

<!--// ===================== 构建请求参数 =====================-->
<!--const buildSearchParams = () => {-->
<!--  const params = {-->
<!--    ...searchParams,-->
<!--    tags: [] as string[],-->
<!--  }-->
<!--  if (selectedCategory.value !== 'all') {-->
<!--    params.category = selectedCategory.value-->
<!--  }-->
<!--  selectedTagList.value.forEach((useTag, index) => {-->
<!--    if (useTag) {-->
<!--      params.tags.push(tagList.value[index])-->
<!--    }-->
<!--  })-->
<!--  return params-->
<!--}-->

<!--// ===================== 核心：获取数据（支持追加模式） =====================-->
<!--/**-->
<!-- * @param append - true: 追加到现有列表; false: 替换列表（默认）-->
<!-- */-->
<!--const fetchData = async (append = false) => {-->
<!--  // 防止重复请求-->
<!--  if (append) {-->
<!--    if (isLoadingMore.value || noMoreData.value) return-->
<!--    isLoadingMore.value = true-->
<!--  } else {-->
<!--    if (loading.value) return-->
<!--    loading.value = true-->
<!--    noMoreData.value = false-->
<!--  }-->

<!--  try {-->
<!--    const params = buildSearchParams()-->
<!--    const res = await listPictureVoByPageUsingPost(params)-->
<!--    if (res.data.code === 0 && res.data.data) {-->
<!--      const records = res.data.data.records ?? []-->
<!--      const newTotal = res.data.data.total ?? 0-->

<!--      if (append) {-->
<!--        // 追加模式：将新数据拼接到列表末尾-->
<!--        dataList.value = [...dataList.value, ...records]-->
<!--      } else {-->
<!--        // 替换模式：直接覆盖-->
<!--        dataList.value = records-->
<!--      }-->
<!--      total.value = newTotal-->

<!--      // 判断是否还有更多数据-->
<!--      if (dataList.value.length >= newTotal || records.length === 0) {-->
<!--        noMoreData.value = true-->
<!--      }-->
<!--    } else {-->
<!--      message.error('获取数据失败，' + res.data.message)-->
<!--    }-->
<!--  } catch (error) {-->
<!--    console.error('获取数据失败', error)-->
<!--    if (!append) {-->
<!--      dataList.value = []-->
<!--      total.value = 0-->
<!--    }-->
<!--  }-->

<!--  if (append) {-->
<!--    isLoadingMore.value = false-->
<!--  } else {-->
<!--    loading.value = false-->
<!--  }-->
<!--}-->

<!--// ===================== 自动填充屏幕检测 =====================-->
<!--/**-->
<!-- * 检查页面内容是否填满了屏幕，如果没有就继续加载-->
<!-- */-->
<!--const autoFillScreen = async () => {-->
<!--  await nextTick() // 等待 DOM 更新-->
<!--  // 判断条件：页面没有滚动条（内容高度 <= 窗口高度），且还有更多数据-->
<!--  const hasScrollbar = document.documentElement.scrollHeight > window.innerHeight-->
<!--  if (!hasScrollbar && !noMoreData.value) {-->
<!--    // 没填满屏幕，自动加载下一页-->
<!--    searchParams.current += 1-->
<!--    await fetchData(true)-->
<!--    // 递归检查，直到填满屏幕或没有更多数据-->
<!--    await autoFillScreen()-->
<!--  }-->
<!--}-->

<!--// ===================== 滚动加载更多（IntersectionObserver） =====================-->
<!--const setupInfiniteScroll = () => {-->
<!--  if (!sentinelRef.value) return-->

<!--  observer = new IntersectionObserver(-->
<!--    (entries) => {-->
<!--      const entry = entries[0]-->
<!--      // 哨兵元素进入视口 => 触发加载更多-->
<!--      if (entry.isIntersecting && !isLoadingMore.value && !noMoreData.value && !loading.value) {-->
<!--        searchParams.current += 1-->
<!--        fetchData(true)-->
<!--      }-->
<!--    },-->
<!--    {-->
<!--      root: null, // 相对于视口-->
<!--      rootMargin: '200px', // 提前 200px 触发加载-->
<!--      threshold: 0,-->
<!--    },-->
<!--  )-->

<!--  observer.observe(sentinelRef.value)-->
<!--}-->

<!--// ===================== 初始加载：一次性加载2页 =====================-->
<!--const initialLoad = async () => {-->
<!--  loading.value = true-->

<!--  // 第一页-->
<!--  searchParams.current = 1-->
<!--  await fetchData(false)-->

<!--  // 第二页（追加）-->
<!--  if (!noMoreData.value) {-->
<!--    searchParams.current = 2-->
<!--    await fetchData(true)-->
<!--  }-->

<!--  loading.value = false-->

<!--  // 检查是否填满屏幕，没填满则继续加载-->
<!--  await autoFillScreen()-->

<!--  // 设置无限滚动监听-->
<!--  setupInfiniteScroll()-->
<!--}-->

<!--// ===================== 页面生命周期 =====================-->
<!--onMounted(() => {-->
<!--  initialLoad()-->
<!--  getTagCategoryOptions()-->
<!--})-->

<!--onUnmounted(() => {-->
<!--  // 清理 Observer，防止内存泄漏-->
<!--  if (observer) {-->
<!--    observer.disconnect()-->
<!--    observer = null-->
<!--  }-->
<!--})-->

<!--// ===================== 分页按钮点击（手动跳转） =====================-->
<!--const onPageChange = async (page: number, pageSize: number) => {-->
<!--  // 重置列表，跳转到指定页-->
<!--  searchParams.current = page-->
<!--  searchParams.pageSize = pageSize-->
<!--  dataList.value = [] // 清空列表-->
<!--  noMoreData.value = false-->

<!--  await fetchData(false)-->

<!--  // 加载该页之后的下一页（保持2页的量）-->
<!--  if (!noMoreData.value) {-->
<!--    searchParams.current += 1-->
<!--    await fetchData(true)-->
<!--  }-->

<!--  // 检查是否填满屏幕-->
<!--  await autoFillScreen()-->

<!--  // 滚动回顶部-->
<!--  window.scrollTo({ top: 0, behavior: 'smooth' })-->
<!--}-->

<!--// ===================== 搜索/筛选（重置并重新加载） =====================-->
<!--const doSearch = async () => {-->
<!--  // 重置到第一页-->
<!--  searchParams.current = 1-->
<!--  dataList.value = []-->
<!--  noMoreData.value = false-->

<!--  await fetchData(false)-->

<!--  // 加载第二页-->
<!--  if (!noMoreData.value) {-->
<!--    searchParams.current = 2-->
<!--    await fetchData(true)-->
<!--  }-->

<!--  // 检查是否填满屏幕-->
<!--  await autoFillScreen()-->
<!--}-->
<!--// const doSearch = () => {-->
<!--//  // 重置搜索条件&ndash;&gt;-->
<!--//   searchParams.current = 1-->
<!--//   fetchData()-->
<!--// }-->

<!--// ===================== 标签和分类列表 =====================-->
<!--const categoryList = ref<string[]>(['摄影', '电商', '表情包', '素材', '海报'])-->
<!--const selectedCategory = ref<string>('all')-->
<!--const tagList = ref<string[]>([-->
<!--  '热门',-->
<!--  '高校',-->
<!--  '二次元',-->
<!--  '生活',-->
<!--  '高清',-->
<!--  '艺术',-->
<!--  '校园',-->
<!--  '背景',-->
<!--  '简历',-->
<!--  '创意',-->
<!--])-->
<!--const selectedTagList = ref<boolean[]>([])-->

<!--const getTagCategoryOptions = async () => {-->
<!--  try {-->
<!--    const res = await listPictureTagCategoryUsingGet()-->
<!--    if (res.data.code === 0 && res.data.data) {-->
<!--      tagList.value = res.data.data.tagList ?? []-->
<!--      categoryList.value = res.data.data.categoryList ?? []-->
<!--    } else {-->
<!--      message.error('获取标签分类列表失败，' + res.data.message)-->
<!--    }-->
<!--  } catch (error) {-->
<!--    console.warn('接口未就绪，使用默认分类和标签')-->
<!--    console.error('获取标签分类失败', error)-->
<!--  }-->
<!--}-->
<!--</script>-->

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'

// ===================== 数据定义 =====================
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const isLoading = ref(false) // 只保留一个加载状态
const noMoreData = ref(false)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const sentinelRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

// ===================== 构建请求参数 =====================
const buildSearchParams = () => {
  const params = { ...searchParams, tags: [] as string[] }
  if (selectedCategory.value !== 'all') params.category = selectedCategory.value
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) params.tags.push(tagList.value[index])
  })
  return params
}

// ===================== 核心：获取数据 =====================
const fetchData = async (isAppend = false) => {
  if (isLoading.value || noMoreData.value) return
  isLoading.value = true

  try {
    const res = await listPictureVoByPageUsingPost(buildSearchParams())
    if (res.data.code === 0 && res.data.data) {
      const records = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0

      dataList.value = isAppend ? [...dataList.value, ...records] : records

      // 简单判断：当前页码 * 每页条数 >= 总数，或者返回空数组
      if (searchParams.current * searchParams.pageSize >= total.value || records.length === 0) {
        noMoreData.value = true
      }
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error) {
    console.error('获取数据失败', error)
  } finally {
    isLoading.value = false
  }
}

// ===================== 滚动加载更多 =====================
const setupInfiniteScroll = () => {
  if (!sentinelRef.value) return
  if (observer) observer.disconnect()

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) {
        searchParams.current += 1
        fetchData(true)
      }
    },
    { rootMargin: '200px' },
  )
  observer.observe(sentinelRef.value)
}

// ===================== 初始加载 =====================
const initialLoad = async () => {
  // 重置状态（其实组件重建时这些本来就是初始值，但写上更保险）
  dataList.value = []
  noMoreData.value = false
  searchParams.current = 1

  await fetchData(false) // 加载第一页

  // 如果第一页没填满且还有数据，自动加载第二页
  if (!noMoreData.value) {
    searchParams.current = 2
    await fetchData(true)
  }

  setupInfiniteScroll()
}

onMounted(() => {
  initialLoad()
  getTagCategoryOptions()
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
    observer = null
  }
})

// ===================== 搜索/筛选 =====================
const doSearch = async () => {
  searchParams.current = 1
  noMoreData.value = false
  dataList.value = []
  await fetchData(false)
}

// ===================== 分页按钮点击 =====================
const onPageChange = async (page: number) => {
  searchParams.current = page
  noMoreData.value = false
  dataList.value = []
  await fetchData(false)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ===================== 标签和分类 =====================
const categoryList = ref<string[]>(['摄影', '电商', '表情包', '素材', '海报'])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([
  '热门',
  '高校',
  '二次元',
  '生活',
  '高清',
  '艺术',
  '校园',
  '背景',
  '简历',
  '创意',
])
const selectedTagList = ref<boolean[]>([])

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      tagList.value = res.data.data.tagList ?? []
      categoryList.value = res.data.data.categoryList ?? []
    }
  } catch (error) {
    console.warn('使用默认分类和标签')
  }
}
</script>

<style scoped>
#homePage {
  /* 做减法：用 min-height 撑开页面，确保哨兵元素能露出来触发滚动加载 */
  min-height: calc(100vh - 100px);
  margin-bottom: 16px;
}
#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}
#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>

<style scoped>
#homePage {
  margin-bottom: 16px;
}

#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}

#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>

<!--<template>-->
<!--  <div id="homePage">-->
<!--    &lt;!&ndash; 搜索框 &ndash;&gt;-->
<!--    <div class="search-bar">-->
<!--      <a-input-search-->
<!--        v-model:value="searchParams.searchText"-->
<!--        placeholder="从海量图片中搜索"-->
<!--        enter-button="搜索"-->
<!--        size="large"-->
<!--        @search="doSearch"-->
<!--      />-->
<!--    </div>-->
<!--    &lt;!&ndash; 分类和标签筛选 &ndash;&gt;-->
<!--    <a-tabs v-model:active-key="selectedCategory" @change="doSearch">-->
<!--      <a-tab-pane key="all" tab="全部" />-->
<!--      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />-->
<!--    </a-tabs>-->
<!--    <div class="tag-bar">-->
<!--      <span style="margin-right: 8px">标签：</span>-->
<!--      <a-space :size="[0, 8]" wrap>-->
<!--        <a-checkable-tag-->
<!--          v-for="(tag, index) in tagList"-->
<!--          :key="tag"-->
<!--          v-model:checked="selectedTagList[index]"-->
<!--          @change="doSearch"-->
<!--        >-->
<!--          {{ tag }}-->
<!--        </a-checkable-tag>-->
<!--      </a-space>-->
<!--    </div>-->
<!--    &lt;!&ndash; 图片列表 &ndash;&gt;-->
<!--    <PictureList :dataList="dataList" :loading="loading" />-->
<!--    &lt;!&ndash; 分页 &ndash;&gt;-->
<!--    <a-pagination-->
<!--      style="text-align: right"-->
<!--      v-model:current="searchParams.current"-->
<!--      v-model:pageSize="searchParams.pageSize"-->
<!--      :total="total"-->
<!--      @change="onPageChange"-->
<!--    />-->
<!--  </div>-->
<!--</template>-->

<!--<script setup lang="ts">-->
<!--import { onMounted, reactive, ref } from 'vue'-->
<!--import {-->
<!--  listPictureTagCategoryUsingGet,-->
<!--  listPictureVoByPageUsingPost,-->
<!--} from '@/api/pictureController.ts'-->
<!--import { message } from 'ant-design-vue'-->
<!--import PictureList from '@/components/PictureList.vue' // 定义数据-->

<!--// 定义数据-->
<!--const dataList = ref<API.PictureVO[]>([])-->
<!--const total = ref(0)-->
<!--const loading = ref(true)-->

<!--// 搜索条件-->
<!--const searchParams = reactive<API.PictureQueryRequest>({-->
<!--  current: 1,-->
<!--  pageSize: 12,-->
<!--  sortField: 'createTime',-->
<!--  sortOrder: 'descend',-->
<!--})-->

<!--// 获取数据-->
<!--const fetchData = async () => {-->
<!--  loading.value = true-->
<!--  // todo: 获取数据，这里添加了try catch，后续不需要，可以try删除，保留try的代码，然后删除catch的全部代码-->
<!--  try {-->
<!--    // 转换搜索参数-->
<!--    const params = {-->
<!--      ...searchParams,-->
<!--      tags: [] as string[],-->
<!--    }-->
<!--    if (selectedCategory.value !== 'all') {-->
<!--      params.category = selectedCategory.value-->
<!--    }-->
<!--    // [true, false, false] => ['java']-->
<!--    selectedTagList.value.forEach((useTag, index) => {-->
<!--      if (useTag) {-->
<!--        params.tags.push(tagList.value[index])-->
<!--      }-->
<!--    })-->
<!--    const res = await listPictureVoByPageUsingPost(params)-->
<!--    if (res.data.code === 0 && res.data.data) {-->
<!--      dataList.value = res.data.data.records ?? []-->
<!--      total.value = res.data.data.total ?? 0-->
<!--    } else {-->
<!--      message.error('获取数据失败，' + res.data.message)-->
<!--    }-->
<!--  } catch (error) {-->
<!--    console.error('获取数据失败', error)-->
<!--    dataList.value = []-->
<!--    total.value = 0-->
<!--  }-->
<!--  loading.value = false-->
<!--}-->

<!--// 页面加载时获取数据，请求一次-->
<!--onMounted(() => {-->
<!--  fetchData()-->
<!--})-->

<!--// 分页参数-->
<!--const onPageChange = (page: number, pageSize: number) => {-->
<!--  searchParams.current = page-->
<!--  searchParams.pageSize = pageSize-->
<!--  fetchData()-->
<!--}-->

<!--// 搜索-->
<!--const doSearch = () => {-->
<!--  // 重置搜索条件-->
<!--  searchParams.current = 1-->
<!--  fetchData()-->
<!--}-->

<!--// 标签和分类列表-->
<!--//todo：获取标签和分类选项，这里ref的value设置了默认值，接口失败时提供默认值。后期可以删除-->
<!--const categoryList = ref<string[]>(['摄影', '电商', '表情包', '素材', '海报'])-->
<!--const selectedCategory = ref<string>('all')-->
<!--const tagList = ref<string[]>([-->
<!--  '热门',-->
<!--  '高校',-->
<!--  '二次元',-->
<!--  '生活',-->
<!--  '高清',-->
<!--  '艺术',-->
<!--  '校园',-->
<!--  '背景',-->
<!--  '简历',-->
<!--  '创意',-->
<!--])-->
<!--const selectedTagList = ref<boolean[]>([])-->
<!--// const categoryList = ref<string[]>([])-->
<!--// const selectedCategory = ref<string>('all')-->
<!--// const tagList = ref<string[]>([])-->
<!--// const selectedTagList = ref<boolean[]>([])-->

<!--/**-->
<!-- * 获取标签和分类选项-->
<!-- * @param values-->
<!-- */-->
<!--const getTagCategoryOptions = async () => {-->
<!--  // todo: 获取标签和分类选项，这里添加了try catch，后续不需要，可以try删除，保留try的代码，然后删除catch的全部代码-->
<!--  try {-->
<!--    const res = await listPictureTagCategoryUsingGet()-->
<!--    if (res.data.code === 0 && res.data.data) {-->
<!--      tagList.value = res.data.data.tagList ?? []-->
<!--      categoryList.value = res.data.data.categoryList ?? []-->
<!--    } else {-->
<!--      message.error('获取标签分类列表失败，' + res.data.message)-->
<!--    }-->
<!--  } catch (error) {-->
<!--    console.warn('接口未就绪，使用默认分类和标签')-->
<!--    console.error('获取标签分类失败', error)-->
<!--    // 接口失败时保留默认值-->
<!--    // console.error('获取标签分类失败', error)-->
<!--    // tagList.value = []-->
<!--    // categoryList.value = []-->
<!--  }-->
<!--}-->

<!--onMounted(() => {-->
<!--  getTagCategoryOptions()-->
<!--})-->
<!--</script>-->

<!--<style scoped>-->
<!--#homePage {-->
<!--  margin-bottom: 16px;-->
<!--}-->

<!--#homePage .search-bar {-->
<!--  max-width: 480px;-->
<!--  margin: 0 auto 16px;-->
<!--}-->

<!--#homePage .tag-bar {-->
<!--  margin-bottom: 16px;-->
<!--}-->
<!--</style>-->
