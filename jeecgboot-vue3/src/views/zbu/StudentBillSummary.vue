<template>
  <div>
    <BasicTable
      @register="registerTable"
      :rowSelection="rowSelection"
      :pagination="{
        pageSize: 20,
        showSizeChanger: true,
        showQuickJumper: true,
        showTotal: (total) => `共 ${total} 条记录`
      }"
    >
      <template #tableTitle>
        <a-button  type="primary" v-auth="'zbu:student_bill:exportXls'" preIcon="ant-design:export-outlined" @click="onExportXls"> 导出</a-button>
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
      </template>

      <template v-slot:bodyCell="{ column, record, text }">
        <template v-if="column.dataIndex === 'studentId'">
          {{ record?.isSummary ? '合计' : (record?.studentId || '') }}
        </template>
        <template v-if="column.dataIndex === 'totalDiscountPrice'">
          {{ record?.isSummary
          ? `¥${record?.totalDiscountPrice?.toFixed(2) || '0.00'}`
          : `¥${(record?.totalDiscountPrice || 0).toFixed(2)}`
          }}
        </template>
      </template>

      <template #action="{ record }">
        <TableAction
          v-if="!record?.isSummary"
          :actions="getTableAction(record)"
        />
      </template>
    </BasicTable>

    <div class="bill-summary-row" v-if="tableData.length > 0">
      <div class="summary-label">合计：</div>
      <div class="summary-value">¥{{ totalDiscountPrice.toFixed(2) }}</div>
    </div>
  </div>
</template>

<script lang="ts" name="zbu-studentBill-summary" setup>
import {ref, reactive, computed, unref, watch} from 'vue';
import {BasicTable, useTable, TableAction} from '/@/components/Table';
import {useListPage} from '/@/hooks/system/useListPage';
import {columns, searchFormSchema} from './StudentBillSummary.data';
import {summaryList, batchDelete} from './StudentBillSummary.api';
import {useUserStore} from '/@/store/modules/user';
import {useMessage} from '/@/hooks/web/useMessage';
import { filterObj } from '/@/utils/common/compUtils';

type Recordable = {[key: string]: any};
const tableData = ref<Recordable[]>([]);
const totalDiscountPrice = ref<number>(0);
const userStore = useUserStore();
const {createMessage} = useMessage();

const isAdmin = computed(() => {
  const roles = userStore.getRoles || [];
  return roles.includes('admin') || roles.includes('sysadmin');
});

const searchParams = reactive({
  schoolYear: '',
  semester: '',
});

const {prefix, tableContext, onExportXls} = useListPage({
  exportConfig: {
    url: '/zbu/studentBill/exportSummary',
    name: '学生账单汇总',
    params: () => {
      return filterObj(searchParams, (key, value) => {
        return value !== '' && value !== undefined && value !== null;
      });
    },
  },
  tableProps: {
    title: '学生账单汇总',
    api: async (params) => {
      const queryParams = {
        ...params,
        studentNo: params.studentNo || '',
        studentName: params.studentName || '',
        className: params.className || '',
        majorName: params.majorName || '',
        schoolYear: params.schoolYear || '',
        semester: params.semester || '',
      };
      const res = await summaryList(queryParams);
      if (res) {
        const rawRecords = res.records || [];
        const formattedRecords = rawRecords.map((item, index) => {
          return {
            ...item,
            id: item.id || `summary-row-${index}`,
          };
        });

        totalDiscountPrice.value = formattedRecords.reduce((sum, item) => {
          return sum + (Number(item.totalDiscountPrice) || 0);
        }, 0);

        if (formattedRecords.length > 0) {
          const summaryRecord: Recordable = {
            id: 'summary-row-total',
            isSummary: true,
            studentId: '合计',
            totalDiscountPrice: totalDiscountPrice.value,
          };
          formattedRecords.push(summaryRecord);
        }

        tableData.value = formattedRecords;
        return {
          records: formattedRecords,
          total: res.total || 0,
        };
      }
      return {records: [], total: 0};
    },
    columns,
    searchInfo: {},
    useSearchForm: true,
    formConfig: {
      schemas: searchFormSchema,
    },
    showIndexColumn: true,
    stripeRows: true,
    bordered: true,
    showActionColumn: false,
    rowKey: 'id',
  },
});

const [registerTable, {reload}, { rowSelection, selectedRowKeys }] = tableContext;

function getTableAction(record) {
  return [
    {
      label: '查看明细',
      onClick: handleViewDetail.bind(null, record),
    },
  ];
}

function handleViewDetail(record: Recordable) {
  createMessage.info(`查看学生 ${record.studentNo} 的账单明细`);
}

async function handleSearch() {
  await reload();
}

async function handleReset() {
  searchParams.schoolYear = '';
  searchParams.semester = '';
  await reload();
}

async function batchHandleDelete() {
  if (selectedRowKeys.value.length === 0) {
    createMessage.warning('请先选择要删除的数据');
    return;
  }

  // 过滤掉自动生成的汇总行
  const validIds = selectedRowKeys.value.filter(key =>
    !key.startsWith('summary-row-') && key !== 'summary-row-total'
  );
  if (validIds.length === 0) {
    createMessage.warning('没有可删除的数据');
    return;
  }

  await batchDelete({ids: validIds}, handleSuccess);
}

async function handleSuccess() {
  selectedRowKeys.value = [];
  reload();
}

watch(() => searchParams, () => {
  reload();
}, {deep: true});
</script>

<style scoped>
.bill-table-container {
  padding: 16px;
}
</style>
