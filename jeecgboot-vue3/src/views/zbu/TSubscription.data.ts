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
    title: '学生姓名',
    align:"center",
    dataIndex: 'studentName'
  },
  {
    title: '教材',
    align:"center",
    dataIndex: 'textbookName'
  },
  {
    title: 'ISBN',
    align:"center",
    dataIndex: 'isbn'
  },
  // {
  //  title: '教材选用',
  //  align:"center",
  //  sorter: true,
  //  dataIndex: 'selectionId_dictText'
  // },
  {
    title: '专业',
    align:"center",
    sorter: true,
    dataIndex: 'majorName'
  },
  {
    title: '学院',
    align:"center",
    sorter: true,
    dataIndex: 'collegeName'
  },
   {
    title: '征订学年',
    align:"center",
    sorter: true,
    dataIndex: 'subscriptionYear'
   },
   {
    title: '征订学期',
    align:"center",
    sorter: true,
    dataIndex: 'subscriptionSemester_dictText'
   },
   {
    title: '征订状态',
    align:"center",
    sorter: true,
    dataIndex: 'subscribeStatus_dictText'
   },

   // {
   //  title: '征订截止时间',
   //  align:"center",
   //  dataIndex: 'deadline'
   // },
   // {
   //  title: '创建日期',
   //  align:"center",
   //  dataIndex: 'createTime'
   // },
   // {
   //  title: '更新日期',
   //  align:"center",
   //  dataIndex: 'updateTime'
   // },
  {
    title: '征订备注',
    align:"center",
    dataIndex: 'remark'
  },
  {
    title: '征订操作时间',
    align:"center",
    dataIndex: 'subscribeTime'
  },
];
//查询数据
export const searchFormSchema: FormSchema[] = [
  {
    label: "新生届号",
    field: 'studentIdPrefix',
    component: 'Input',
    //colProps: {span: 6},
  },
	{
      label: "学生",
      field: 'studentId',
      component: 'Input',
      //colProps: {span: 6},
 	},
	{
      label: "专业",
      field: 'majorId',
      component: 'Input',
      //colProps: {span: 6},
 	},
  {
    label: "学院",
    field: 'collegeName',
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
      component: 'JDictSelectTag',
      componentProps:{
          dictCode:"semester"
      },
      //colProps: {span: 6},
 	},
	{
      label: "征订状态",
      field: 'subscribeStatus',
      component: 'JDictSelectTag',
      componentProps:{
          dictCode:"subscribe_status"
      },
      //colProps: {span: 6},
 	},
];
//表单数据
export const formSchema: FormSchema[] = [
  {
    label: '学生',
    field: 'studentId',
    component: 'JLinkTableCard',
    componentProps: {
     valueField: 'id',
     textField: 'student_id,student_name',
     tableName: 't_student',
     multi: false
    },
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入学生!'},
          ];
     },
  },
  {
    label: '教材',
    field: 'textbookId',
    component: 'JLinkTableCard',
    componentProps: {
     valueField: 'id',
     textField: 'textbook_name',
     tableName: 't_textbook',
     multi: false
    },
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入教材!'},
          ];
     },
  },
  {
    label: '教材选用',
    field: 'selectionId',
    component: 'JLinkTableCard',
    componentProps: {
     valueField: 'id',
     textField: 'selection_status',
     tableName: 't_textbook_selection',
     multi: false
    },
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入教材选用!'},
          ];
     },
  },
  {
    label: '专业',
    field: 'majorId',
    component: 'JLinkTableCard',
    componentProps: {
     valueField: 'id',
     textField: 'major_name',
     tableName: 't_major',
     multi: false
    },
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入专业!'},
          ];
     },
  },
  {
    label: '征订学年',
    field: 'subscriptionYear',
    component: 'Input',
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入征订学年!'},
          ];
     },
  },
  {
    label: '征订学期',
    field: 'subscriptionSemester',
    component: 'JDictSelectTag',
    componentProps:{
        dictCode:"semester",
     },
    dynamicRules: ({model,schema}) => {
          return [
                 { required: true, message: '请输入征订学期!'},
          ];
     },
  },
  {
    label: '征订状态',
    field: 'subscribeStatus',
    component: 'JDictSelectTag',
    componentProps:{
        dictCode:"subscribe_status",
     },
  },
  {
    label: '征订备注',
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


// 学生专用表单（仅2个字段，确保无拼写错误）
export const studentEditFormSchema: FormSchema[] = [
  {
    label: '征订状态',
    field: 'subscribeStatus', // 字段名必须和列表的dataIndex一致
    component: 'JDictSelectTag', // 确保组件名正确
    componentProps:{
      dictCode:"subscribe_status", // 确保字典码在系统中存在
    },
    dynamicRules: () => [{ required: true, message: '请选择征订状态!' }],
    colProps: { span: 24 } // 占满宽度，确保显示
  },
  {
    label: '',
    field: 'id',
    component: 'Input',
    show: false // 隐藏ID，仅提交用
  }
];

// 高级查询数据
export const superQuerySchema = {
  studentId: {title: '学生',order: 0,view: 'link_table', type: 'string',},
  textbookId: {title: '教材',order: 1,view: 'link_table', type: 'string',},
  selectionId: {title: '教材选用',order: 2,view: 'link_table', type: 'string',},
  majorId: {title: '专业',order: 3,view: 'link_table', type: 'string',},
  subscriptionYear: {title: '征订学年',order: 4,view: 'text', type: 'string',},
  subscriptionSemester: {title: '征订学期',order: 5,view: 'list', type: 'string',dictCode: 'semester',},
  subscribeStatus: {title: '征订状态',order: 6,view: 'list', type: 'string',dictCode: 'subscribe_status',},
  remark: {title: '征订备注',order: 7,view: 'text', type: 'string',},
  subscribeTime: {title: '征订操作时间',order: 8,view: 'datetime', type: 'string',},
  // deadline: {title: '征订截止时间',order: 9,view: 'datetime', type: 'string',},
  createTime: {title: '创建日期',order: 10,view: 'datetime', type: 'string',},
  updateTime: {title: '更新日期',order: 11,view: 'datetime', type: 'string',},
};

/**
* 流程表单调用这个方法获取formSchema
* @param param
*/
export function getBpmFormSchema(_formData): FormSchema[]{
  // 默认和原始表单保持一致 如果流程中配置了权限数据，这里需要单独处理formSchema
  return formSchema;
}
