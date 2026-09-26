import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElAlert, ElButton, ElCard } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElAlert)
app.use(ElButton)
app.use(ElCard)
app.mount('#app')
