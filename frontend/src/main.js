import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElConfigProvider, ElAlert, ElButton, ElCard, ElTable, ElTableColumn, ElInput, ElSelect, ElOption, ElPagination, ElDialog, ElForm, ElFormItem, ElTag, ElLoading } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElConfigProvider)
app.use(ElAlert)
app.use(ElButton)
app.use(ElCard)
app.use(ElTable)
app.use(ElTableColumn)
app.use(ElInput)
app.use(ElSelect)
app.use(ElOption)
app.use(ElPagination)
app.use(ElDialog)
app.use(ElForm)
app.use(ElFormItem)
app.use(ElTag)
app.use(ElLoading)
app.mount('#app')
