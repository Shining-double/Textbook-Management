import {BasicColumn} from '/@/components/Table';
import {FormSchema} from '/@/components/Table';
import { rules} from '/@/utils/helper/validator';
import { render } from '/@/utils/common/renderUtils';
import { getWeekMonthQuarterYear } from '/@/utils';
//列表数据
export const columns: BasicColumn[] = [
  {
    title: '学号',
    align:"center",
    sorter: true,
    dataIndex: 'studentNo'
  },
  {
    title: '姓名',
    align:"center",
    sorter: false,
    dataIndex: 'studentName' // 匹配api返回的studentName字段，无需异步渲染
  },
  {
    title: '专业',
    align:"center",
    sorter: true,
    dataIndex: 'majorName' // 关键：删掉_dictText，后端返回的是majorName
  },
  {
    title: '征订学年',
    align:"center",
    sorter: true,
    dataIndex: 'subscriptionYear' // 关键：删掉_dictText，后端返回的是subscriptionYear
  },
  {
    title: '征订学期',
    align:"center",
    sorter: true,
    dataIndex: 'subscriptionSemester', // 关键：删掉_dictText，后端返回的是subscriptionSemester
    // 新增：把数字1/2转成中文（可选，优化显示）
    customRender: ({ text }) => {
      return text === '1' ? '第一学期' : text === '2' ? '第二学期' : text;
    }
  },
  {
    title: '教材名称',
    align:"center",
    dataIndex: 'textbookName' // 关键：删掉_dictText，后端返回的是textbookName
  },
  {
    title: 'ISBN',
    align:"center",
    dataIndex: 'isbn'
  },
  {
    title: '教材定价',
    align:"center",
    dataIndex: 'price' // 关键：删掉_dictText，后端返回的是price
  },
  {
    title: '教材费用',
    align:"center",
    dataIndex: 'discountPrice' // 原本就对，不用改
  },
  {
    title: '征订状态',
    align:"center",
    sorter: true,
    dataIndex: 'subscribeStatus', // 关键：删掉_dictText，后端返回的是subscribeStatus
    // 新增：把数字1转成中文（可选，优化显示）
    customRender: ({ text }) => {
      return text === '1' ? '已征订' : text;
    }
  },
  {
    title: '领取状态',
    align:"center",
    sorter: true,
    dataIndex: 'receiveStatus', // 关键：删掉_dictText，后端返回的是receiveStatus
    // 新增：把数字1转成中文（可选，优化显示）
    customRender: ({ text }) => {
      return text === '1' ? '已领取' : text;
    }
  },
  {
    title: '备注',
    align:"center",
    dataIndex: 'remark' // 原本就对，不用改
  },
];
//查询数据
export const searchFormSchema: FormSchema[] = [
  {
    label: "学号",
    field: 'studentId',
    component: 'Input',
    //colProps: {span: 6},
  },
  {
    label: "专业",
    field: 'majorName',
    component: 'Input',
    //colProps: {span: 6},
  },
  {
    label: "征订学年",
    field: 'subscriptionYear',
    component: 'Input',
    //colProps: {span: 6},
  },
  {
    label: "征订学期",
    field: 'subscriptionSemester',
    component: 'Input',
    //colProps: {span: 6},
  },
  {
    label: "征订状态",
    field: 'subscribeStatus',
    component: 'Input',
    //colProps: {span: 6},
  },
  {
    label: "领取状态",
    field: 'receiveStatus',
    component: 'Input',
    //colProps: {span: 6},
  },
];
//表单数据
export const formSchema: FormSchema[] = [
  {
    label: '学号',
    field: 'studentId',
    component: 'Input',
    disabled: true,
  },
  {
    label: '专业',
    field: 'majorName',
    component: 'Input',
    disabled: true,
  },
  {
    label: '征订学年',
    field: 'subscriptionYear',
    component: 'Input',
    disabled: true,
  },
  {
    label: '征订学期',
    field: 'subscriptionSemester',
    component: 'Input',
    disabled: true,
  },
  {
    label: '教材名称',
    field: 'textbookName',
    component: 'Input',
    disabled: true,
  },
  {
    label: '教材定价',
    field: 'price',
    component: 'InputNumber',
  },
  {
    label: '教材费用',
    field: 'discountPrice',
    component: 'InputNumber',
  },
  {
    label: '征订状态',
    field: 'subscribeStatus',
    component: 'JDictSelectTag',
    componentProps:{
      dictCode:"subscribe_status"
    },
  },
  {
    label: '领取状态',
    field: 'receiveStatus',
    component: 'JDictSelectTag',
    componentProps:{
      dictCode:"receive_status"
    },
  },
  {
    label: '备注',
    field: 'remark',
    component: 'Input',
  },
  // TODO 主键隐藏字段，目前写死为ID
  {
    label: '',
    field: 'id',
    component: 'Input',
    show: false
  },
];

// 高级查询数据
export const superQuerySchema = {
  studentId: {title: '学号',order: 0,view: 'link_table', type: 'string',},
  majorName: {title: '专业',order: 1,view: 'link_table', type: 'string',},
  subscriptionYear: {title: '征订学年',order: 2,view: 'link_table', type: 'string',},
  subscriptionSemester: {title: '征订学期',order: 3,view: 'link_table', type: 'string',},
  textbookName: {title: '教材名称',order: 4,view: 'link_table', type: 'string',},
  price: {title: '教材定价',order: 5,view: 'number', type: 'number',},
  discountPrice: {title: '教材费用',order: 6,view: 'number', type: 'number',},
  subscribeStatus: {title: '征订状态',order: 7,view: 'link_table', type: 'string',},
  receiveStatus: {title: '领取状态',order: 8,view: 'link_table', type: 'string',},
  remark: {title: '备注',order: 9,view: 'text', type: 'string',},
};

/**
 * 流程表单调用这个方法获取formSchema
 * @param param
 */
export function getBpmFormSchema(_formData): FormSchema[]{
  // 默认和原始表单保持一致 如果流程中配置了权限数据，这里需要单独处理formSchema
  return formSchema;
}
