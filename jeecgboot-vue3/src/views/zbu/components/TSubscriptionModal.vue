<template>
  <BasicModal v-bind="$attrs" @register="registerModal" destroyOnClose :title="title" :maxHeight="500" :width="800" @ok="handleSubmit">
    <!-- 表单渲染 -->
    <BasicForm @register="registerForm" name="TSubscriptionForm" />
  </BasicModal>
</template>

<script lang="ts" setup>
import {ref, computed, unref, reactive, watch} from 'vue';
import {BasicModal, useModalInner} from '/@/components/Modal';
import {BasicForm, useForm} from '/@/components/Form/index';
// 导入表单配置
import {formSchema, studentEditFormSchema} from '../TSubscription.data';
import {saveOrUpdate} from '../TSubscription.api';
import { useMessage } from '/@/hooks/web/useMessage';
import { getDateByPicker } from '/@/utils';

const { createMessage } = useMessage();

// 1. 接收父组件的学生编辑标识
const props = defineProps({
  isStudentEdit: {
    type: Boolean,
    default: false
  }
});

// Emits声明
const emit = defineEmits(['register','success']);
const isUpdate = ref(false);
const isDetail = ref(false);

// 2. 核心：直接根据学生模式初始化对应schema（简化逻辑，避免切换问题）
const finalSchema = computed(() => {
  console.log('当前是否为学生模式：', props.isStudentEdit);
  console.log('加载的schema：', props.isStudentEdit ? '学生专用（仅征订状态）' : '完整表单');
  return props.isStudentEdit ? studentEditFormSchema : formSchema;
});

// 3. 初始化表单：直接用最终schema，不动态切换
const [registerForm, { setProps, resetFields, setFieldsValue, validate }] = useForm({
  labelWidth: 150,
  schemas: unref(finalSchema), // 直接用计算后的schema
  showActionButtonGroup: false,
  baseColProps: {span: 24},
  baseRowStyle: { padding: "0 20px" }
});

// 4. Modal打开逻辑（极简版）
const [registerModal, {setModalProps, closeModal}] = useModalInner(async (data) => {
  // 重置表单
  await resetFields();

  // 设置Modal属性
  setModalProps({
    confirmLoading: false,
    showCancelBtn: !!data?.showFooter,
    showOkBtn: !!data?.showFooter
  });

  // 标记状态
  isUpdate.value = !!data?.isUpdate;
  isDetail.value = !data?.showFooter;

  // 禁用控制：详情禁用，编辑启用
  setProps({
    disabled: !data?.showFooter
  });

  // 表单赋值（仅给当前schema的字段）
  if (isUpdate.value && data.record) {
    if (props.isStudentEdit) {
      // 学生模式：仅赋值ID（隐藏）+ 征订状态
      await setFieldsValue({
        id: data.record.id,
        subscribeStatus: data.record.subscribeStatus
      });
      console.log('学生模式赋值：', {id: data.record.id, subscribeStatus: data.record.subscribeStatus});
    } else {
      // 管理员模式：赋值所有字段
      await setFieldsValue({...data.record});
    }
  }
});

// 标题
const title = computed(() => {
  return props.isStudentEdit ? '编辑征订状态' : (isUpdate.value ? (isDetail.value ? '详情' : '编辑') : '新增');
});

// 表单提交（极简版）
async function handleSubmit() {
  try {
    // 验证表单
    const values = await validate();
    setModalProps({confirmLoading: true});

    // 提交数据：学生模式仅传ID+征订状态
    const submitData = props.isStudentEdit
      ? { id: values.id, subscribeStatus: values.subscribeStatus }
      : values;

    // 提交接口
    await saveOrUpdate(submitData, isUpdate.value);

    // 关闭弹窗+刷新
    closeModal();
    emit('success');
    createMessage.success(props.isStudentEdit ? '征订状态修改成功' : '操作成功');
  } catch (error) {
    console.error('提交失败：', error);
    return Promise.reject(error);
  } finally {
    setModalProps({confirmLoading: false});
  }
}

// 日期处理（仅管理员模式）
const changeDateValue = (formData) => {
  const fieldPickers = reactive({});
  if (formData && fieldPickers) {
    for (let key in fieldPickers) {
      if (formData[key]) {
        formData[key] = getDateByPicker(formData[key], fieldPickers[key]);
      }
    }
  }
};
</script>

<style lang="less" scoped>
/** 移除所有强制隐藏的样式！！！ */
:deep(.ant-input-number) {
  width: 100%;
}

:deep(.ant-calendar-picker) {
  width: 100%;
}

/* 仅保留必要样式，删除所有display: none的样式 */
</style>
