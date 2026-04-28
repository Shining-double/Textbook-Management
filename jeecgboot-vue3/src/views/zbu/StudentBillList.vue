<template>
  <div>
    <div class="bill-table-container">
      <BasicTable
        @register="registerTable"
        :rowSelection="rowSelection"
        :pagination="{
          pageSize: 10,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条记录`
        }"
      >
        <template #tableTitle>
          <div class="search-btn-container" v-if="isStudent">
            <super-query :config="superQueryConfig" @search="handleSuperQuery" />
          </div>
          <template v-if="isAdmin">
            <a-button type="primary" v-auth="'zbu:student_bill:add'" @click="handleAdd" preIcon="ant-design:plus-outlined"> 新增</a-button>
            <a-button type="primary" v-auth="'zbu:student_bill:sync'" @click="handleSync" preIcon="ant-design:sync-outlined" style="margin-left: 8px;"> 同步征订数据</a-button>
            <a-button  type="primary" v-auth="'zbu:student_bill:exportXls'" preIcon="ant-design:export-outlined" @click="onExportXls"> 导出</a-button>
            <!--            <j-upload-button type="primary" v-auth="'zbu:student_bill:importExcel'" preIcon="ant-design:import-outlined" @click="onImportXls">导入</j-upload-button>-->
            <a-dropdown v-if="selectedRowKeys.length > 0">
              <template #overlay>
                <a-menu>
                  <a-menu-item key="1" @click="batchHandleDelete">
                    <Icon icon="ant-design:delete-outlined"></Icon>
                    删除
                  </a-menu-item>
                </a-menu>
              </template>
              <a-button v-auth="'zbu:student_bill:deleteBatch'">批量操作
                <Icon icon="mdi:chevron-down"></Icon>
              </a-button>
            </a-dropdown>
            <!--            <super-query :config="superQueryConfig" @search="handleSuperQuery" />-->
          </template>
        </template>

        <template v-slot:bodyCell="{ column, record, text }">
          <template v-if="column.dataIndex === 'studentNo'">
            {{ record?.isSummary ? '合计' : (record?.studentNo || '未知学号') }}
          </template>
          <template v-if="column.dataIndex === 'studentName'">
            {{ record?.isSummary ? '' : (record?.studentName || '未知姓名') }}
          </template>
          <template v-if="column.dataIndex === 'textbookName'">
            {{ record?.isSummary ? '' : (record?.textbookName || '未知教材') }}
          </template>
          <template v-if="column.dataIndex === 'majorName'">
            {{ record?.isSummary ? '' : (record?.majorName || '未知专业') }}
          </template>
          <template v-if="column.dataIndex === 'discountPrice'">
            {{ record?.isSummary
            ? `¥${record?.discountPrice?.toFixed(2) || '0.00'}`
            : `¥${(record?.discountPrice || 0).toFixed(2)}`
            }}
          </template>
        </template>
      </BasicTable>

      <div class="bill-summary-row" v-if="isAdmin && tableData.length > 0">
        <div class="summary-label">合计：</div>
        <div class="summary-value">¥{{ totalDiscountPrice.toFixed(2) }}</div>
      </div>
    </div>

    <StudentBillModal
      @register="registerModal"
      @success="handleSuccess"
    ></StudentBillModal>
  </div>
</template>

<script lang="ts" name="zbu-studentBill" setup>
import {ref, reactive, computed, unref, watch} from 'vue';
import {BasicTable, useTable, TableAction} from '/@/components/Table';
import {useModal} from '/@/components/Modal';
import { useListPage } from '/@/hooks/system/useListPage'
import StudentBillModal from './components/StudentBillModal.vue'
import {columns, searchFormSchema, superQuerySchema} from './StudentBill.data';
import {
  list, deleteOne, batchDelete, getImportUrl, getExportUrl,
  syncFromSubscription
} from './StudentBill.api';
import { useUserStore } from '/@/store/modules/user';
import { useMessage } from '/@/hooks/web/useMessage';
import { getDateByPicker } from '/@/utils';

type Recordable = { [key: string]: any };
const tableData = ref<Recordable[]>([]);
const totalDiscountPrice = ref<number>(0);

const fieldPickers = reactive({});
const queryParam = reactive<any>({});
const userStore = useUserStore();
const { createMessage } = useMessage();

const [registerModal, {openModal}] = useModal();

const isAdmin = computed(() => {
  const userInfo = userStore.getUserInfo || {};
  const username = (userInfo.username || '').toLowerCase().trim();
  return ['admin', 'sysadmin'].includes(username);
});

const isStudent = computed(() => {
  return !unref(isAdmin);
});

const studentSearchSchema = computed(() => [
  {
    field: 'subscriptionYear',
    label: '征订年份',
    component: 'Input',
    componentProps: {
      placeholder: '请输入征订年份（如2025）',
      style: { width: '180px' }
    },
  },
  {
    field: 'subscriptionSemester',
    label: '征订学期',
    component: 'Select',
    componentProps: {
      style: { width: '180px' },
      placeholder: '请选择学期',
      options: [
        { label: '第一学期', value: '1' },
        { label: '第二学期', value: '2' }
      ]
    },
  },
  {
    field: 'textbookName',
    label: '教材名称',
    component: 'Input',
    componentProps: {
      placeholder: '请输入教材名称',
      style: { width: '200px' }
    },
  },
]);

const fetchBillList = async (params = {}) => {
  try {
    const requestParams = { ...params, ...queryParam };
    const userInfo = userStore.getUserInfo || {};
    const loginUsername = userInfo.username || "";

    if (unref(isStudent)) {
      if (!loginUsername) {
        createMessage.warning("未获取到你的登录学号，请重新登录");
        return { records: [], total: 0 };
      }
      requestParams.studentNo = loginUsername;
      requestParams.student_id = loginUsername;
    }

    const res = await list(requestParams);
    const rawRecords = res.records || [];
    const formattedRecords: Recordable[] = [];

    for (const item of rawRecords) {
      const studentNo = item.studentNo || item.student_id || item.studentId || "未知学号";
      const studentName = item.studentName || "未知姓名";
      const majorName = item.majorName || "未知专业";
      const textbookName = item.textbookName || "未知教材";

      formattedRecords.push({
        ...item,
        id: item.id || `bill-${Math.random().toString(36).slice(2, 10)}`,
        studentId: item.student_id || item.studentId,
        subscriptionId: item.subscription_id || item.subscriptionId,
        textbookId: item.textbook_id || item.textbookId,
        majorId: item.major_id || item.majorId,
        receiveId: item.receive_id || item.receiveId,
        studentNo,
        studentName,
        majorName,
        textbookName,
        isbn: item.isbn || '',
        subscriptionYear: item.subscriptionYear || item.subscription_year,
        subscriptionSemester: item.subscriptionSemester || item.subscription_semester,
        subscribeStatus: item.subscribeStatus || item.subscribe_status,
        receiveStatus: item.receiveStatus || item.receive_status,
        price: item.price,
        discountPrice: item.discountPrice,
        remark: item.remark,
      });
    }

    let finalRecords = [...formattedRecords];
    if (unref(isStudent) && Object.keys(requestParams).length > 0) {
      if (requestParams.subscriptionYear) {
        const year = requestParams.subscriptionYear.trim();
        finalRecords = finalRecords.filter(item => (item.subscriptionYear || '').includes(year));
      }
      if (requestParams.subscriptionSemester) {
        finalRecords = finalRecords.filter(item => item.subscriptionSemester === requestParams.subscriptionSemester);
      }
      if (requestParams.textbookName) {
        const textbook = requestParams.textbookName.trim().toLowerCase();
        finalRecords = finalRecords.filter(item => (item.textbookName || '').toLowerCase().includes(textbook));
      }
    }

    totalDiscountPrice.value = finalRecords.reduce((sum, item) => {
      return sum + (Number(item.discountPrice) || 0);
    }, 0);

    // ========== 关键修改：管理员/学生端都添加表格内汇总行 ==========
    if (finalRecords.length > 0) {
      const summaryRecord: Recordable = {
        id: 'summary-row',
        isSummary: true,
        studentNo: '合计',
        studentName: '',
        majorName: '',
        textbookName: '',
        discountPrice: totalDiscountPrice.value,
        subscriptionYear: '',
        subscriptionSemester: '',
      };
      finalRecords.push(summaryRecord);
    }

    tableData.value = finalRecords;
    return { records: finalRecords, total: res.total || finalRecords.length };
  } catch (e) {
    console.error("【获取账单列表失败】", e);
    createMessage.error("获取账单记录失败，请刷新重试");
    tableData.value = [];
    totalDiscountPrice.value = 0;
    return { records: [], total: 0 };
  }
};

const { tableContext, onExportXls, onImportXls } = useListPage({
  tableProps:{
    title: '个人账单',
    api: fetchBillList,
    columns: columns,
    canResize:true,
    showActionColumn: false,
    formConfig: {
      schemas: unref(isAdmin) ? searchFormSchema : unref(studentSearchSchema),
      autoSubmitOnEnter: true,
      showAdvancedButton: unref(isAdmin),
      fieldMapToNumber: [],
      fieldMapToTime: [],
      show: unref(isAdmin) || unref(isStudent),
    },
    beforeFetch: (params) => {
      if (params && fieldPickers) {
        for (let key in fieldPickers) {
          if (params[key]) {
            params[key] = getDateByPicker(params[key], fieldPickers[key]);
          }
        }
      }
      return params;
    },
  },
  exportConfig: {
    name:"个人账单",
    url: getExportUrl,
    params: queryParam,
    show: unref(isAdmin)
  },
  importConfig: {
    url: getImportUrl,
    success: handleSuccess,
    show: false //隐藏导入 unref(isAdmin)
  },
});

const [registerTable, {reload}, { rowSelection, selectedRowKeys }] = tableContext;

const superQueryConfig = reactive(superQuerySchema);

watch([tableData], () => {
  totalDiscountPrice.value = tableData.value.reduce((sum, item) => {
    return sum + (Number(item.discountPrice) || 0);
  }, 0);
}, { deep: true });

async function handleSync() {
  if (!unref(isAdmin)) {
    createMessage.warning("仅管理员可执行同步操作");
    return;
  }
  try {
    createMessage.loading('正在同步征订数据，请稍候...');
    const res = await syncFromSubscription();
    createMessage.success(res.msg || '同步征订数据成功！');
    reload();
  } catch (e: any) {
    createMessage.error(e.msg || '同步征订数据时发生错误，请检查后端日志！');
  }
}

function handleSuperQuery(params) {
  Object.assign(queryParam, params);
  reload();
}

function handleAdd() {
  if (!unref(isAdmin)) {
    createMessage.warning("仅管理员可新增账单");
    return;
  }
  openModal(true, {
    isUpdate: false,
    showFooter: true,
  });
}

function handleEdit(record: Recordable) {
  openModal(true, {
    record,
    isUpdate: true,
    showFooter: true,
  });
}

function handleDetail(record: Recordable) {
  openModal(true, {
    record,
    isUpdate: true,
    showFooter: false,
  });
}

async function handleDelete(record) {
  if (!unref(isAdmin)) {
    createMessage.warning("仅管理员可删除账单");
    return;
  }
  await deleteOne({id: record.id}, handleSuccess);
}

async function batchHandleDelete() {
  if (!unref(isAdmin)) {
    createMessage.warning("仅管理员可批量删除账单");
    return;
  }
  const validIds = selectedRowKeys.value.filter(key => key !== 'summary-row');
  if (validIds.length === 0) {
    createMessage.warning("请选择要删除的账单记录");
    return;
  }
  await batchDelete({ids: validIds}, handleSuccess);
}

function handleSuccess() {
  selectedRowKeys.value = [];
  reload();
}

function getTableAction(record) {
  if (unref(isAdmin)) {
    return [
      {
        label: '编辑',
        onClick: handleEdit.bind(null, record),
        auth: 'zbu:student_bill:edit'
      }
    ];
  } else {
    return [
      {
        label: '编辑',
        onClick: handleEdit.bind(null, record),
        type: 'primary'
      }
    ];
  }
}

function getDropDownAction(record) {
  if (unref(isAdmin)) {
    return [
      {
        label: '详情',
        onClick: handleDetail.bind(null, record),
      }, {
        label: '删除',
        popConfirm: {
          title: '是否确认删除',
          confirm: handleDelete.bind(null, record),
          placement: 'topLeft',
        },
        auth: 'zbu:student_bill:delete'
      }
    ];
  } else {
    return [
      {
        label: '详情',
        onClick: handleDetail.bind(null, record),
      }
    ];
  }
}
</script>

<style lang="less" scoped>
:deep(.ant-input), :deep(.ant-picker), :deep(.ant-select-selector), :deep(.ant-input-number) {
  width: 200px;
  box-sizing: border-box;
}

:deep(.ant-select) {
  width: 200px !important;
  min-width: 200px !important;
  flex: none !important;
  display: inline-flex;
  align-items: center;
}

:deep(.ant-select-selection-item) {
  white-space: nowrap !important;
}

:deep(.ant-table-actions .ant-btn-primary) {padding: 0 8px;margin-right: 4px;}

:deep(.ant-form-item-control-input-content) {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

:deep(.ant-form-item-label) {
  width: 80px;
  text-align: right;
}

:deep(.ant-form) {
  padding: 8px 0;
}

.bill-table-container {
  position: relative;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  margin: 10px 0;
}

.bill-summary-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 24px;
  background: #fafafa;
  border-top: 2px solid #f0f0f0;
  margin-top: -1px;

  .summary-label {
    font-weight: bold;
    color: #333;
    margin-right: 8px;
    margin-left: 16px;
  }

  .summary-value {
    font-weight: bold;
    color: #165DFF;
    font-size: 14px;
  }
}

:deep(.ant-table-tbody tr:last-child) {
  font-weight: bold;
  background-color: #fafafa;
  border-top: 2px solid #f0f0f0;
}

:deep(.ant-table-pagination) {
  margin: 16px !important;
  text-align: right;
}
</style>
