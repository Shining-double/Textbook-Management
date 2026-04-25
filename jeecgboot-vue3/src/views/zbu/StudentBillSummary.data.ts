import {BasicColumn} from '/@/components/Table';
import {FormSchema} from '/@/components/Table';

export const columns: BasicColumn[] = [
  {
    title: '学号',
    align:"center",
    dataIndex: 'studentId',
    width: 120
  },
  {
    title: '姓名',
    align:"center",
    dataIndex: 'studentName',
    width: 100
  },
  {
    title: '专业',
    align:"center",
    dataIndex: 'majorName',
    width: 150
  },
  {
    title: '征订学年',
    align:"center",
    dataIndex: 'schoolYear',
    width: 100
  },
  {
    title: '征订学期',
    align:"center",
    dataIndex: 'semester',
    width: 100
  },
  {
    title: '教材费用',
    align:"center",
    dataIndex: 'totalDiscountPrice',
    width: 120,
    customRender: ({ text }) => text ? `¥${Number(text).toFixed(2)}` : '¥0.00'
  },
];

export const searchFormSchema: FormSchema[] = [
  {
    label: "学号",
    field: 'studentNo',
    component: 'Input',
  },
  {
    label: "姓名",
    field: 'studentName',
    component: 'Input',
  },
  {
    label: "班级",
    field: 'className',
    component: 'Input',
  },
  {
    label: "专业",
    field: 'majorName',
    component: 'Input',
  },
  {
    label: "征订学年",
    field: 'schoolYear',
    component: 'Input',
  },
  {
    label: "征订学期",
    field: 'semester',
    component: 'Select',
    componentProps: {
      options: [
        { label: '第一学期', value: '1' },
        { label: '第二学期', value: '2' },
      ],
    },
  },
];
