<script setup lang="ts">
import { ref } from "vue";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@ulticode/design-system/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@ulticode/design-system/ui/select";
import { Textarea } from "@ulticode/design-system/ui/textarea";
import { Button } from "@ulticode/design-system/ui/button";
import { Label } from "@ulticode/design-system/ui/label";
import { apiPost } from "@/utils/request";
import { toast } from "vue-sonner";

const props = defineProps<{
  entityType: string;
  entityId: string;
}>();

const emit = defineEmits<{
  success: [];
}>();

const open = ref(false);
const loading = ref(false);
const category = ref("");
const reason = ref("");
const evidence = ref("");

const categories = [
  { value: "SPAM", label: "垃圾信息" },
  { value: "HARASSMENT", label: "骚扰" },
  { value: "HATE_SPEECH", label: "仇恨言论" },
  { value: "VIOLENCE", label: "暴力内容" },
  { value: "SEXUAL_CONTENT", label: "色情内容" },
  { value: "MISINFORMATION", label: "虚假信息" },
  { value: "WRONG_ANSWER", label: "错误答案" },
  { value: "COPYRIGHT", label: "版权侵权" },
  { value: "OTHER", label: "其他" },
];

function handleOpen() {
  open.value = true;
  category.value = "";
  reason.value = "";
  evidence.value = "";
}

async function handleSubmit() {
  if (!category.value) {
    toast.error("请选择举报原因");
    return;
  }
  loading.value = true;
  try {
    await apiPost("/moderation/reports", {
      entityType: props.entityType,
      entityId: props.entityId,
      category: category.value,
      reason: reason.value,
      ...(evidence.value ? { evidence: evidence.value } : {}),
    });
    toast.success("举报已提交");
    open.value = false;
    emit("success");
  } catch {
    toast.error("提交失败");
  } finally {
    loading.value = false;
  }
}

defineExpose({ open: handleOpen });
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>举报内容</DialogTitle>
        <DialogDescription> 请选择举报原因并提供详细说明 </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="category">举报原因</Label>
          <Select v-model="category">
            <SelectTrigger id="category">
              <SelectValue placeholder="选择举报原因" />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup
                ><SelectItem
                  v-for="cat in categories"
                  :key="cat.value"
                  :value="cat.value"
                >
                  {{ cat.label }}
                </SelectItem></SelectGroup
              >
            </SelectContent>
          </Select>
        </div>
        <div class="grid gap-2">
          <Label for="reason">详细说明</Label>
          <Textarea id="reason" v-model="reason" rows="3" class="resize-none" />
        </div>
        <div class="grid gap-2">
          <Label for="evidence">证据链接</Label>
          <Textarea
            id="evidence"
            v-model="evidence"
            rows="2"
            placeholder="提供相关证据或链接（可选）"
            class="resize-none"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="open = false"> 取消 </Button>
        <Button variant="destructive" :disabled="loading" @click="handleSubmit">
          {{ loading ? "提交中..." : "提交举报" }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
