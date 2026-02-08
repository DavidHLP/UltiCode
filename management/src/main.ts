import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import 'vue-sonner/style.css'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { setupGlobalErrorHandler } from './plugins/error-handler'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)

// Setup global error handler
setupGlobalErrorHandler(app)

app.mount('#app')
